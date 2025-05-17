package me.wojnowski.oidc4s.testkit

import cats.Applicative
import cats.data.NonEmptySet
import cats.effect.Clock
import cats.implicits._
import me.wojnowski.oidc4s.IdTokenClaims.Audience
import me.wojnowski.oidc4s.IdTokenVerifier.Error.CouldNotDecodeClaim
import me.wojnowski.oidc4s.ClientId
import me.wojnowski.oidc4s.IdTokenClaims
import me.wojnowski.oidc4s.IdTokenVerifier
import me.wojnowski.oidc4s.Issuer
import me.wojnowski.oidc4s.json.JsonDecoder.ClaimsDecoder
import me.wojnowski.oidc4s.json.JsonDecoder
import me.wojnowski.oidc4s.json.JsonSupport
import scala.annotation.unused

object IdTokenVerifierMock {

  def constRawClaims[F[_]: Applicative](rawClaims: String)(implicit jsonSupport: JsonSupport): IdTokenVerifier[F] =
    constRawClaimsEither(Right(rawClaims))

  def constRawClaimsEither[F[_]: Applicative](rawClaimsEither: Either[IdTokenVerifier.Error, String])(implicit jsonSupport: JsonSupport)
    : IdTokenVerifier[F] = constRawClaimsEitherPF(_ => rawClaimsEither)

  def constRawClaimsEitherPF[F[_]: Applicative](
    rawTokenToRawClaimsEither: PartialFunction[String, Either[IdTokenVerifier.Error, String]]
  )(implicit jsonSupport: JsonSupport
  ): IdTokenVerifier[F] = new IdTokenVerifier[F] {
    import jsonSupport._

    override def verify(rawToken: String, expectedClientId: ClientId): F[Either[IdTokenVerifier.Error, IdTokenClaims.Subject]] =
      verifyAndDecode(rawToken).map(_.flatMap { claims =>
        Either.cond(
          claims.matchesClientId(expectedClientId),
          claims.subject,
          IdTokenVerifier.Error.ClientIdDoesNotMatch
        )
      })

    override def verifyAndDecode(rawToken: String): F[Either[IdTokenVerifier.Error, IdTokenClaims]] =
      rawTokenToRawClaimsEither
        .lift(rawToken)
        .toRight(IdTokenVerifier.Error.MalformedToken: IdTokenVerifier.Error)
        .flatten
        .flatMap { rawClaims =>
          JsonDecoder[IdTokenClaims]
            .decode(rawClaims)
            .leftMap(IdTokenVerifier.Error.CouldNotDecodeClaim(_): IdTokenVerifier.Error)
        }
        .pure[F]

    override def verifyAndDecode(rawToken: String, expectedClientId: ClientId): F[Either[IdTokenVerifier.Error, IdTokenClaims]] =
      verifyAndDecode(rawToken).map(_.filterOrElse(_.matchesClientId(expectedClientId), IdTokenVerifier.Error.ClientIdDoesNotMatch))

    override def verifyAndDecodeCustom[A](rawToken: String)(implicit decoder: ClaimsDecoder[A]): F[Either[IdTokenVerifier.Error, A]] =
      rawTokenToRawClaimsEither
        .lift(rawToken)
        .toRight(IdTokenVerifier.Error.MalformedToken: IdTokenVerifier.Error)
        .flatten
        .flatMap { rawClaims =>
          ClaimsDecoder[A]
            .decode(rawClaims)
            .map(_._1)
            .leftMap(IdTokenVerifier.Error.CouldNotDecodeClaim(_): IdTokenVerifier.Error)
        }
        .pure[F]

    override def verifyAndDecodeCustom[A](rawToken: String, expectedClientId: ClientId)(implicit decoder: ClaimsDecoder[A])
      : F[Either[IdTokenVerifier.Error, A]] =
      rawTokenToRawClaimsEither
        .lift(rawToken)
        .toRight(IdTokenVerifier.Error.MalformedToken: IdTokenVerifier.Error)
        .flatten
        .flatMap { rawClaims =>
          ClaimsDecoder[A]
            .decode(rawClaims)
            .leftMap(IdTokenVerifier.Error.CouldNotDecodeClaim(_): IdTokenVerifier.Error)
            .flatMap { case (customClaims, standardClaims) =>
              Either.cond(
                standardClaims.matchesClientId(expectedClientId),
                customClaims,
                IdTokenVerifier.Error.ClientIdDoesNotMatch
              )
            }

        }
        .pure[F]

  }

  def constSubject[F[_]: Applicative: Clock](subject: IdTokenClaims.Subject, clientId: ClientId = ClientId("https://example.com"))
    : IdTokenVerifier[F] =
    constSubjectEither[F](Right(subject), clientId)

  def constSubjectEither[F[_]: Applicative: Clock](
    errorOrSubject: Either[IdTokenVerifier.Error, IdTokenClaims.Subject],
    clientId: ClientId = ClientId("https://example.com")
  ): IdTokenVerifier[F] =
    constSubjectPF[F]((_: String) => errorOrSubject, clientId)

  def constSubjectPF[F[_]: Applicative: Clock](
    rawTokenToSubjectPF: PartialFunction[String, Either[IdTokenVerifier.Error, IdTokenClaims.Subject]],
    clientId: ClientId = ClientId("https://example.com")
  ): IdTokenVerifier[F] =
    constClaimsEitherPF(
      rawTokenToSubjectPF.map { errorOrSubject =>
        Applicative[F].map(Clock[F].realTimeInstant) { now =>
          errorOrSubject.map(subject =>
            IdTokenClaims(
              Issuer("https://example.com"),
              subject,
              NonEmptySet.of(Audience(clientId.value)),
              expiration = now.plusSeconds(600),
              issuedAt = now
            )
          )
        }
      }
    )

  def constClaims[F[_]: Applicative](claims: IdTokenClaims): IdTokenVerifier[F] = constClaimsEither(Right(claims))

  def constClaimsEither[F[_]: Applicative](claimsEither: Either[IdTokenVerifier.Error, IdTokenClaims]): IdTokenVerifier[F] =
    constClaimsEitherPF[F](_ => claimsEither.pure[F])

  def constClaimsEitherPF[F[_]: Applicative](
    rawTokenToClaimsPF: PartialFunction[String, F[Either[IdTokenVerifier.Error, IdTokenClaims]]]
  ): IdTokenVerifier[F] = new IdTokenVerifier[F] {

    override def verifyAndDecode(rawToken: String): F[Either[IdTokenVerifier.Error, IdTokenClaims]] =
      Applicative[F].map(
        rawTokenToClaimsPF
          .lift(rawToken)
          .toRight(IdTokenVerifier.Error.MalformedToken: IdTokenVerifier.Error)
          .sequence
      )(_.flatten)

    override def verifyAndDecode(rawToken: String, expectedClientId: ClientId): F[Either[IdTokenVerifier.Error, IdTokenClaims]] =
      verifyAndDecode(rawToken).map(_.filterOrElse(_.matchesClientId(expectedClientId), IdTokenVerifier.Error.ClientIdDoesNotMatch))

    override def verify(rawToken: String, expectedClientId: ClientId): F[Either[IdTokenVerifier.Error, IdTokenClaims.Subject]] =
      Applicative[F].map(verifyAndDecode(rawToken)) {
        _.flatMap { standardClaims =>
          Either.cond(
            standardClaims.matchesClientId(expectedClientId),
            standardClaims.subject,
            IdTokenVerifier.Error.ClientIdDoesNotMatch
          )
        }
      }

    override def verifyAndDecodeCustom[A](rawToken: String)(implicit @unused decoder: ClaimsDecoder[A])
      : F[Either[IdTokenVerifier.Error, A]] =
      Applicative[F].map(
        rawTokenToClaimsPF
          .lift(rawToken)
          .toRight(IdTokenVerifier.Error.MalformedToken: IdTokenVerifier.Error)
          .sequence
      ) {
        _.flatten.flatMap { _ =>
          CouldNotDecodeClaim("mock").asLeft[A]
        }
      }

    override def verifyAndDecodeCustom[A](rawToken: String, expectedClientId: ClientId)(implicit decoder: ClaimsDecoder[A])
      : F[Either[IdTokenVerifier.Error, A]] =
      verifyAndDecodeCustom(rawToken)

  }

}
