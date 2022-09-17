package me.wojnowski.oids4s.json.circe

import io.circe.Decoder
import me.wojnowski.oidc4s.Audience
import me.wojnowski.oidc4s.AuthenticationContextClassReference
import me.wojnowski.oidc4s.AuthenticationMethodReference
import me.wojnowski.oidc4s.AuthorizedParty
import me.wojnowski.oidc4s.IdTokenClaims
import me.wojnowski.oidc4s.Issuer
import me.wojnowski.oidc4s.Nonce
import me.wojnowski.oidc4s.Subject

import java.time.Instant

trait IdTokenCirceDecoder {
  private implicit val issuerDecoder: Decoder[Issuer] =
    Decoder[String].map(Issuer.apply)
  private implicit val subjectDecoder: Decoder[Subject] =
    Decoder[String].map(Subject.apply)
  private implicit val audienceDecoder: Decoder[Audience] =
    Decoder[String].map(Audience.apply)
  private implicit val nonceDecoder: Decoder[Nonce] =
    Decoder[String].map(Nonce.apply)
  private implicit val accrDecoder: Decoder[AuthenticationContextClassReference] =
    Decoder[String].map(AuthenticationContextClassReference.apply)
  private implicit val amrDecoder: Decoder[AuthenticationMethodReference] =
    Decoder[String].map(AuthenticationMethodReference.apply)
  private implicit val authorizedPartyDecoder: Decoder[AuthorizedParty] =
    Decoder[String].map(AuthorizedParty.apply)

  protected implicit val jwtIdTokenDecoder: Decoder[IdTokenClaims] =
    Decoder.forProduct10[
      IdTokenClaims,
      Issuer,
      Subject,
      Either[Audience, Set[Audience]],
      Long,
      Long,
      Option[Long],
      Option[Nonce],
      Option[AuthenticationContextClassReference],
      Option[List[AuthenticationMethodReference]],
      Option[AuthorizedParty]
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
        issuer: Issuer,
        subject: Subject,
        audience: Either[Audience, Set[Audience]],
        expiration: Long,
        issuedAt: Long,
        authenticationTime: Option[Long],
        nonce: Option[Nonce],
        authenticationContextClassReference: Option[AuthenticationContextClassReference],
        authenticationMethodsReference: Option[List[AuthenticationMethodReference]],
        authenticationParty: Option[AuthorizedParty]
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
        case Left(_)       =>
          hCursor.as[B].map(Right(_))
        case Right(string) =>
          Right(Left(string))
      }
    }

}
