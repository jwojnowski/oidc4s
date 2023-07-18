package me.wojnowski.oidc4s.json.circe

import cats.data.NonEmptySet
import io.circe.Decoder
import me.wojnowski.oidc4s.IdTokenClaims
import me.wojnowski.oidc4s.IdTokenClaims._
import me.wojnowski.oidc4s.Issuer

import java.time.Instant

trait IdTokenCirceDecoder extends IssuerCirceDecoder with AudienceCirceDecoder {

  private implicit val subjectDecoder: Decoder[Subject] =
    Decoder[String].map(Subject.apply)
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
      NonEmptySet[Audience],
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
        audience: NonEmptySet[Audience],
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
          audience = audience,
          expiration = Instant.ofEpochSecond(expiration),
          issuedAt = Instant.ofEpochSecond(issuedAt),
          authenticationTime = authenticationTime.map(Instant.ofEpochSecond),
          nonce = nonce,
          authenticationContextClassReference = authenticationContextClassReference,
          authenticationMethodsReference = authenticationMethodsReference.getOrElse(List.empty),
          authorizedParty = authenticationParty
        )
    }

}
