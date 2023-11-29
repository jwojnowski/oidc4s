package me.wojnowski.oidc4s.testkit

import cats.Applicative
import cats.Traverse
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

  @deprecated("Use version with explicit client ID")
  def constSubject[F[_]: Applicative: Traverse: Clock](subject: IdTokenClaims.Subject): IdTokenVerifier[F] =
    constSubject[F](subject, ClientId("https://example.com"))

  def constSubject[F[_]: Applicative: Clock](subject: IdTokenClaims.Subject, clientId: ClientId = ClientId("https://example.com"))
    : IdTokenVerifier[F] =
    constSubjectEither[F](Right(subject), clientId)

  @deprecated("Use version with explicit client ID")
  def constSubjectEither[F[_]: Applicative: Traverse: Clock](errorOrSubject: Either[IdTokenVerifier.Error, IdTokenClaims.Subject])
    : IdTokenVerifier[F] = constSubjectEither[F](errorOrSubject, ClientId("https://example.com"))

  def constSubjectEither[F[_]: Applicative: Clock](
    errorOrSubject: Either[IdTokenVerifier.Error, IdTokenClaims.Subject],
    clientId: ClientId = ClientId("https://example.com")
  ): IdTokenVerifier[F] =
    constSubjectPF[F]((_: String) => errorOrSubject, clientId)

  @deprecated("Use version with explicit client ID")
  def constSubjectPF[F[_]: Applicative: Traverse: Clock](
    rawTokenToSubjectPF: PartialFunction[String, Either[IdTokenVerifier.Error, IdTokenClaims.Subject]]
  ): IdTokenVerifier[F] =
    constSubjectPF[F](rawTokenToSubjectPF, ClientId("https://example.com"))

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

  @deprecated("Use constClaims", "0.12.2")
  def constStandardClaims[F[_]: Applicative: Traverse](claims: IdTokenClaims): IdTokenVerifier[F] = constClaims(claims)

  @deprecated("Use constClaimsEither", "0.12.2")
  def constStandardClaimsEither[F[_]: Applicative: Traverse](claimsEither: Either[IdTokenVerifier.Error, IdTokenClaims])
    : IdTokenVerifier[F] =
    constClaimsEither(claimsEither)

  @deprecated("Use constClaimsEitherPF", "0.12.2")
  def constStandardClaimsEitherPF[F[_]: Applicative: Traverse](
    rawTokenToClaimsPF: PartialFunction[String, F[Either[IdTokenVerifier.Error, IdTokenClaims]]]
  ): IdTokenVerifier[F] = constClaimsEitherPF(rawTokenToClaimsPF)

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

    override def verifyAndDecodeCustom[A](rawToken: String)(implicit decoder: ClaimsDecoder[A]): F[Either[IdTokenVerifier.Error, A]] =
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
