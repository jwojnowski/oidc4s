package me.wojnowski.oidc4s

import cats.Monad
import cats.data.EitherT
import cats.data.NonEmptySet
import cats.effect.Clock
import cats.syntax.all._
import me.wojnowski.oidc4s.IdTokenClaims.Audience
import me.wojnowski.oidc4s.IdTokenVerifier.Error.CouldNotExtractKeyId
import me.wojnowski.oidc4s.IdTokenVerifier.Error.JwtVerificationError
import me.wojnowski.oidc4s.config.OpenIdConnectDiscovery
import me.wojnowski.oidc4s.json.JsonDecoder
import me.wojnowski.oidc4s.json.JsonDecoder.ClaimsDecoder
import me.wojnowski.oidc4s.json.JsonSupport
import pdi.jwt.Jwt
import pdi.jwt.JwtAlgorithm
import pdi.jwt.JwtHeader

import java.nio.charset.StandardCharsets
import java.security.PublicKey
import java.time.ZoneId
import java.time.{Clock => JavaClock}
import java.util.Base64
import scala.util.Try

trait IdTokenVerifier[F[_]] {

  /** Verifies a token is valid and has been issued for a particular client ID. Returns Subject if so. */
  def verify(rawToken: String, expectedClientId: ClientId): F[Either[IdTokenVerifier.Error, IdTokenClaims.Subject]]

  /** Verifies a token is valid. Returns standard Open ID Token claims. Client ID must be checked manually. */
  def verifyAndDecode(rawToken: String): F[Either[IdTokenVerifier.Error, IdTokenClaims]]

  /** Verifies a token is valid. Returns custom type decoded using provided decoder.
    */
  def verifyAndDecodeCustom[A](rawToken: String)(implicit decoder: ClaimsDecoder[A]): F[Either[IdTokenVerifier.Error, A]]

  /** Verifies a token is valid and has been issued for a particular client ID. Returns custom type decoded using provided decoder.
    */
  def verifyAndDecodeCustom[A](rawToken: String, expectedClientId: ClientId)(implicit decoder: ClaimsDecoder[A])
    : F[Either[IdTokenVerifier.Error, A]]

}

object IdTokenVerifier {

  @deprecated("Use instance", "0.11.0")
  def create[F[_]: Monad: Clock](
    publicKeyProvider: PublicKeyProvider[F],
    discovery: OpenIdConnectDiscovery[F],
    jsonSupport: JsonSupport
  ): IdTokenVerifier[F] =
    this.discovery(publicKeyProvider, discovery, jsonSupport)

  def discovery[F[_]: Monad: Clock](
    publicKeyProvider: PublicKeyProvider[F],
    discovery: OpenIdConnectDiscovery[F],
    jsonSupport: JsonSupport
  ): IdTokenVerifier[F] =
    instance(
      publicKeyProvider,
      issuerF = discovery.getConfig.map(_.bimap(IdTokenVerifier.Error.CouldNotDiscoverConfig.apply, _.issuer)),
      jsonSupport
    )

  def static[F[_]: Monad: Clock](publicKeyProvider: PublicKeyProvider[F], issuer: Issuer, jsonSupport: JsonSupport): IdTokenVerifier[F] =
    instance(
      publicKeyProvider,
      issuerF = issuer.asRight[IdTokenVerifier.Error.CouldNotDiscoverConfig].pure[F],
      jsonSupport
    )

  def instance[F[_]: Monad: Clock](
    publicKeyProvider: PublicKeyProvider[F],
    issuerF: F[Either[IdTokenVerifier.Error.CouldNotDiscoverConfig, Issuer]],
    jsonSupport: JsonSupport
  ): IdTokenVerifier[F] =
    new IdTokenVerifier[F] {
      import jsonSupport._

      // According to OIDC RFC, only RS256 should be supported
      private val supportedAlgorithms = Seq(JwtAlgorithm.RS256, JwtAlgorithm.RS384, JwtAlgorithm.RS512)

      override def verify(rawToken: String, expectedClientId: ClientId): F[Either[Error, IdTokenClaims.Subject]] =
        verifyAndDecode(rawToken).map(_.ensure(Error.ClientIdDoesNotMatch)(_.matchesClientId(expectedClientId)).map(_.subject))

      override def verifyAndDecode(rawToken: String): F[Either[IdTokenVerifier.Error, IdTokenClaims]] =
        verifyAndDecodeCustom[IdTokenClaims](rawToken)(JsonDecoder[IdTokenClaims].decode(_).map(result => (result, result)))

      override def verifyAndDecodeCustom[A](rawToken: String)(implicit decoder: ClaimsDecoder[A]): F[Either[Error, A]] =
        internalVerifyAndDecode(rawToken, _ => Either.unit)

      override def verifyAndDecodeCustom[A](rawToken: String, expectedClientId: ClientId)(implicit decoder: ClaimsDecoder[A])
        : F[Either[IdTokenVerifier.Error, A]] =
        internalVerifyAndDecode(
          rawToken,
          claims => Either.cond(claims.matchesClientId(expectedClientId), (), IdTokenVerifier.Error.ClientIdDoesNotMatch)
        )

      private def internalVerifyAndDecode[A](
        rawToken: String,
        standardClaimsCheck: IdTokenClaims => Either[IdTokenVerifier.Error, Unit]
      )(implicit decoder: ClaimsDecoder[A]
      ): F[Either[IdTokenVerifier.Error, A]] = {
        for {
          issuer     <- EitherT(issuerF)
          instant    <- EitherT.liftF(Clock[F].realTimeInstant)
          javaClock = JavaClock.fixed(instant, ZoneId.of("UTC"))
          headerJson <- EitherT.fromEither(extractHeaderJson(rawToken))
          kid        <- EitherT.fromEither(extractKid(headerJson))
          publicKey  <- EitherT(publicKeyProvider.getKey(kid).map(_.leftMap(IdTokenVerifier.Error.CouldNotFindPublicKey.apply)))
          result     <- EitherT.fromEither {
                          decodeAndVerifyToken[(A, IdTokenClaims)](rawToken, javaClock, publicKey)
                            .flatMap { case (claims, standardClaims) =>
                              ensureExpectedIssuer(tokenIssuer = standardClaims.issuer, expectedIssuer = issuer)
                                .leftWiden[IdTokenVerifier.Error]
                                .flatTap { _ =>
                                  standardClaimsCheck(standardClaims)
                                }
                                .as(claims)
                            }
                        }
        } yield result
      }.value

      private def decodeAndVerifyToken[A: JsonDecoder](
        rawToken: String,
        javaClock: JavaClock,
        publicKey: PublicKey
      ): Either[Error, A] =
        Jwt(javaClock)
          .decodeRaw(rawToken, publicKey, supportedAlgorithms)
          .toEither
          .leftMap[Error](throwable => JwtVerificationError(throwable))
          .flatMap { rawClaims =>
            JsonDecoder[A]
              .decode(rawClaims)
              .leftMap(IdTokenVerifier.Error.CouldNotDecodeClaim.apply)
          }

      private def ensureExpectedIssuer(tokenIssuer: Issuer, expectedIssuer: Issuer): Either[Error.UnexpectedIssuer, Unit] =
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

  sealed trait Error extends ProductSerializableNoStackTrace

  object Error {
    case object ClientIdDoesNotMatch extends Error

    case class CouldNotDiscoverConfig(cause: OpenIdConnectDiscovery.Error) extends Error

    case object CouldNotExtractHeader extends Error

    case object CouldNotExtractKeyId extends Error

    case class CouldNotFindPublicKey(cause: PublicKeyProvider.Error) extends Error

    case class CouldNotDecodeClaim(details: String) extends Error

    case class JwtVerificationError(cause: Throwable) extends Error

    case class UnexpectedIssuer(found: Issuer, expected: Issuer) extends Error
  }

}
