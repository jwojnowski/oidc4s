package me.wojnowski.oidc4s

import cats.Monad
import cats.data.EitherT
import cats.effect.Clock
import cats.syntax.all._
import me.wojnowski.oidc4s.IdTokenVerifier.Error.CouldNotDecodeClaim
import me.wojnowski.oidc4s.IdTokenVerifier.Error.CouldNotDecodeHeader
import me.wojnowski.oidc4s.IdTokenVerifier.Error.InvalidSignature
import me.wojnowski.oidc4s.IdTokenVerifier.Error.InvalidToken
import me.wojnowski.oidc4s.IdTokenVerifier.Error.TokenExpired
import me.wojnowski.oidc4s.IdTokenVerifier.Error.UnsupportedAlgorithm
import me.wojnowski.oidc4s.config.OpenIdConnectDiscovery
import me.wojnowski.oidc4s.json.JsonDecoder
import me.wojnowski.oidc4s.json.JsonDecoder.ClaimsDecoder
import me.wojnowski.oidc4s.json.JsonSupport

import java.nio.charset.StandardCharsets
import java.security.PublicKey
import java.security.Signature
import java.time.Instant
import java.time.ZoneId
import java.time.{Clock => JavaClock}
import java.util.Base64
import scala.util.Success
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
          now        <- EitherT.liftF(Clock[F].realTimeInstant)
          headerJson <- EitherT.fromEither(extractHeaderJson(rawToken))
          header     <- EitherT.fromEither(decodeHeader(headerJson))
          publicKey  <- EitherT(publicKeyProvider.getKey(header.keyId).map(_.leftMap(IdTokenVerifier.Error.CouldNotFindPublicKey.apply)))
          result     <- EitherT.fromEither {
                          decodeJwtAndVerifySignature[A](rawToken, publicKey, header).flatMap { case (claims, standardClaims) =>
                            List(
                              ensureNotExpired(now, standardClaims.expiration),
                              ensureExpectedIssuer(tokenIssuer = standardClaims.issuer, expectedIssuer = issuer),
                              standardClaimsCheck(standardClaims)
                            ).sequence.as(claims)
                          }
                        }
        } yield result
      }.value

      private def ensureExpectedIssuer(tokenIssuer: Issuer, expectedIssuer: Issuer): Either[Error.UnexpectedIssuer, Unit] =
        Either.cond(expectedIssuer === tokenIssuer, (), IdTokenVerifier.Error.UnexpectedIssuer(tokenIssuer, expectedIssuer))

      private def ensureNotExpired(now: Instant, expiresAt: Instant): Either[Error.TokenExpired, Unit] =
        Either.raiseWhen(expiresAt.isBefore(now))(TokenExpired(since = expiresAt))

      private def decodeHeader(headerJson: String): Either[CouldNotDecodeHeader, JwtHeader] =
        JsonDecoder[JwtHeader]
          .decode(headerJson)
          .leftMap(CouldNotDecodeHeader.apply)

      private def decodeJwtAndVerifySignature[A: ClaimsDecoder](rawToken: String, key: PublicKey, header: JwtHeader)
        : Either[Error, (A, IdTokenClaims)] =
        rawToken.split('.') match {
          case Array(rawHeader, rawClaims, rawSignature) =>
            for {
              _      <- verifyAlgorithm(header.algorithm)
              _      <- verifySignature(header.algorithm.fullName, key, rawHeader, rawClaims, rawSignature)
              result <- parseClaims[A](rawClaims)
            } yield result

          case _ =>
            InvalidToken.asLeft
        }

      private def parseClaims[A: ClaimsDecoder](rawClaims: String): Either[CouldNotDecodeClaim, (A, IdTokenClaims)] =
        Try {
          new String(Base64.getUrlDecoder.decode(rawClaims))
        }.toEither.leftMap(t => CouldNotDecodeClaim(t.getMessage)).flatMap { rawJson =>
          ClaimsDecoder[A].decode(rawJson).leftMap(CouldNotDecodeClaim.apply)
        }

      private def verifyAlgorithm(algorithm: Algorithm) =
        Either.raiseUnless(Algorithm.supportedAlgorithms.contains_(algorithm))(UnsupportedAlgorithm(algorithm.name.some))

      private def verifySignature(
        signingAlgorithm: String,
        publicKey: PublicKey,
        rawHeader: String,
        rawClaims: String,
        rawSignature: String
      ) =
        Try {
          val decodedSignature = Base64.getUrlDecoder.decode(rawSignature)
          val signatureInstance = Signature.getInstance(signingAlgorithm)
          signatureInstance.initVerify(publicKey)
          signatureInstance.update(s"$rawHeader.$rawClaims".getBytes(StandardCharsets.UTF_8))
          signatureInstance.verify(decodedSignature)
        } match {
          case Success(true) => Either.unit
          case _             => InvalidSignature.asLeft
        }

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

    case class CouldNotDecodeHeader(details: String) extends Error

    case class CouldNotDecodeClaim(details: String) extends Error

    case class TokenExpired(since: Instant) extends Error

    case object InvalidToken extends Error

    case object InvalidSignature extends Error

    case class UnsupportedAlgorithm(providedAlgorithm: Option[String]) extends Error

    case class UnexpectedIssuer(found: Issuer, expected: Issuer) extends Error
  }

}
