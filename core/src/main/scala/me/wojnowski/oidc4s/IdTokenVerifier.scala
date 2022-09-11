package me.wojnowski.oidc4s

import cats.Monad
import cats.data.EitherT
import cats.effect.Clock
import pdi.jwt.JwtAlgorithm
import pdi.jwt.JwtClaim
import pdi.jwt.exceptions.JwtException

import java.nio.charset.StandardCharsets
import java.security.PublicKey
import java.time.ZoneId
import java.util.Base64
import scala.util.Try
import cats.syntax.all._
import me.wojnowski.oidc4s.IdTokenVerifier.Error.CouldNotExtractKeyId
import me.wojnowski.oidc4s.IdTokenVerifier.Error.JwtVerificationError
import pdi.jwt.Jwt
import pdi.jwt.JwtHeader

import java.time.{Clock => JavaClock}

trait IdTokenVerifier[F[_]] {
  def verifyAndDecode(rawToken: String): F[Either[IdTokenVerifier.Error, IdTokenClaims]]

  def verifyAndDecodeFullResult(rawToken: String): F[Either[IdTokenVerifier.Error, IdTokenVerifier.Result]]
}

object IdTokenVerifier {

  def create[F[_]: Monad: Clock](
    publicKeyProvider: PublicKeyProvider[F],
    discovery: OpenIdConnectDiscovery[F],
    jsonSupport: JsonSupport
  ): IdTokenVerifier[F] =
    new IdTokenVerifier[F] {
      import jsonSupport._

      private val supportedAlgorithms = Seq(JwtAlgorithm.RS256)

      override def verifyAndDecode(rawToken: String): F[Either[IdTokenVerifier.Error, IdTokenClaims]] =
        verifyAndDecodeFullResult(rawToken).map(_.map(_.decodedClaims))

      // TODO rename?
      override def verifyAndDecodeFullResult(rawToken: String): F[Either[IdTokenVerifier.Error, IdTokenVerifier.Result]] = {
        for {
          config     <- EitherT(discovery.getConfig).leftMap(IdTokenVerifier.Error.CouldNotDiscoverConfig.apply)
          instant    <- EitherT.liftF(Clock[F].realTimeInstant)
          javaClock = JavaClock.fixed(instant, ZoneId.of("UTC"))
          headerJson <- EitherT.fromEither(extractHeaderJson(rawToken))
          kid        <- EitherT.fromEither(extractKid(headerJson))
          publicKey  <- EitherT(publicKeyProvider.getKey(kid).map(_.leftMap(IdTokenVerifier.Error.CouldNotFindPublicKey.apply)))
          result     <- EitherT.fromEither(decodeAndVerifyToken(rawToken, javaClock, publicKey, config.issuer)).map {
                          case (decodedClaims, claimsJson) =>
                            Result(headerJson, claimsJson, decodedClaims)
                        }
        } yield result
      }.value

      private def decodeAndVerifyToken(
        rawToken: String,
        javaClock: JavaClock,
        publicKey: PublicKey,
        expectedIssuer: String
      ): Either[Error, (IdTokenClaims, String)] =
        Jwt(javaClock)
          .decodeRaw(rawToken, publicKey, supportedAlgorithms)
          .toEither
          .leftMap[Error](throwable => JwtVerificationError(throwable))
          .flatMap { rawClaims =>
            JsonDecoder[IdTokenClaims]
              .decode(rawClaims)
              .leftMap(IdTokenVerifier.Error.CouldNotDecodeClaim.apply)
              .map((_, rawClaims))
          }
          .flatTap { case (idTokenClaims, _) => ensureExpectedIssuer(idTokenClaims.issuer, expectedIssuer) }

      // TODO Issuer type
      private def ensureExpectedIssuer(tokenIssuer: String, expectedIssuer: String): Either[Error.UnexpectedIssuer, Unit] =
        Either.cond(expectedIssuer === tokenIssuer, (), IdTokenVerifier.Error.UnexpectedIssuer(tokenIssuer, expectedIssuer))

      private def extractKid(headerJson: String): Either[CouldNotExtractKeyId.type, String] =
        JsonDecoder[JwtHeader]
          .decode(headerJson)
          .toOption
          .flatMap(_.keyId)
          .toRight(CouldNotExtractKeyId)

      private def extractHeaderJson(rawToken: String) =
        Try {
          new String(
            Base64.getDecoder.decode(rawToken.takeWhile(_ != '.')),
            StandardCharsets.UTF_8
          )
        }.toEither.leftMap(_ => Error.CouldNotExtractHeader)

    }

  case class Result(rawHeader: String, rawClaims: String, decodedClaims: IdTokenClaims)

  sealed trait Error extends ProductSerializableNoStacktrace

  object Error {
    case class CouldNotDiscoverConfig(cause: OpenIdConnectDiscovery.Error) extends Error

    // TODO extract, decode, partially decode?
    case object CouldNotExtractHeader extends Error

    case object CouldNotExtractKeyId extends Error

    case class CouldNotFindPublicKey(cause: PublicKeyProvider.Error) extends Error

    case class CouldNotDecodeClaim(details: String) extends Error

    case class JwtVerificationError(cause: Throwable) extends Error

    case class UnexpectedIssuer(found: String, expected: String) extends Error
  }

}
