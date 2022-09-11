package me.wojnowski.oidc4s.circe

import io.circe.Decoder
import me.wojnowski.oidc4s.IdTokenClaims

import java.time.Instant

trait IdTokenCirceDecoder {
  protected implicit val jwtIdTokenDecoder: Decoder[IdTokenClaims] =
    Decoder.forProduct10[
      IdTokenClaims,
      String,
      String,
      Either[String, Set[String]],
      Long,
      Long,
      Option[Long],
      Option[String],
      Option[String],
      Option[List[String]],
      Option[String]
    ](
      "iss",
      "sub",
      "aud",
      "exp",
      "iat",
      "auth_time",
      "nonce",
      "acr",
      "amr",
      "azp"
    ) {
      (
        issuer: String,
        subject: String,
        audience: Either[String, Set[String]],
        expiration: Long,
        issuedAt: Long,
        authenticationTime: Option[Long],
        nonce: Option[String],
        authenticationContextClassReference: Option[String],
        authenticationMethodsReference: Option[List[String]],
        authenticationParty: Option[String]
      ) =>
        IdTokenClaims(
          issuer = issuer,
          subject = subject,
          audience = audience.fold(Set(_), identity),
          expiration = Instant.ofEpochSecond(expiration),
          issuedAt = Instant.ofEpochSecond(issuedAt),
          authenticationTime = authenticationTime.map(Instant.ofEpochSecond),
          nonce = nonce,
          authenticationContextClassReference = authenticationContextClassReference,
          authenticationMethodsReference = authenticationMethodsReference.getOrElse(List.empty),
          authorizedParty = authenticationParty
        )
    }

  private implicit def fallbackEitherDecoder[A: Decoder, B: Decoder]: Decoder[Either[A, B]] =
    Decoder.instance { hCursor =>
      hCursor.as[A] match {
        case Left(_) =>
          hCursor.as[B].map(Right(_))
        case Right(string) =>
          Right(Left(string))
      }
    }
}
