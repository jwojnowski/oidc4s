package me.wojnowski.oidc4s.json.ziojson

import me.wojnowski.oidc4s.IdTokenClaims
import me.wojnowski.oidc4s.IdTokenClaims._
import me.wojnowski.oidc4s.Issuer

import cats.data.NonEmptySet

import java.time.Instant

import zio.json._

trait IdTokenZioJsonDecoder extends IssuerZioJsonDecoder with AudienceZioJsonDecoder {

  private implicit val subjectDecoder: JsonDecoder[Subject] =
    JsonDecoder[String].map(Subject.apply)
  private implicit val nonceDecoder: JsonDecoder[Nonce] =
    JsonDecoder[String].map(Nonce.apply)
  private implicit val accrDecoder: JsonDecoder[AuthenticationContextClassReference] =
    JsonDecoder[String].map(AuthenticationContextClassReference.apply)
  private implicit val amrDecoder: JsonDecoder[AuthenticationMethodReference] =
    JsonDecoder[String].map(AuthenticationMethodReference.apply)
  private implicit val authorizedPartyDecoder: JsonDecoder[AuthorizedParty] =
    JsonDecoder[String].map(AuthorizedParty.apply)

  private case class IdTokenClaimsRaw(
    @jsonField("iss") issuer: Issuer,
    @jsonField("sub") subject: Subject,
    @jsonField("aud") audience: NonEmptySet[Audience],
    @jsonField("exp") expiration: Long,
    @jsonField("iat") issuedAt: Long,
    @jsonField("auth_time") authenticationTime: Option[Long],
    @jsonField("nonce") nonce: Option[Nonce],
    @jsonField("acr") authenticationContextClassReference: Option[AuthenticationContextClassReference],
    @jsonField("amr") authenticationMethodsReference: Option[List[AuthenticationMethodReference]],
    @jsonField("azp") authorizedParty: Option[AuthorizedParty]
  )

  private object IdTokenClaimsRaw {
    implicit val decoder: JsonDecoder[IdTokenClaimsRaw] =
      DeriveJsonDecoder.gen[IdTokenClaimsRaw]
  }

  protected implicit val idTokenClaimsZioJsonDecoder: JsonDecoder[IdTokenClaims] =
    IdTokenClaimsRaw.decoder.map { raw =>
      IdTokenClaims(
        issuer = raw.issuer,
        subject = raw.subject,
        audience = raw.audience,
        expiration = Instant.ofEpochSecond(raw.expiration),
        issuedAt = Instant.ofEpochSecond(raw.issuedAt),
        authenticationTime = raw.authenticationTime.map(Instant.ofEpochSecond),
        nonce = raw.nonce,
        authenticationContextClassReference = raw.authenticationContextClassReference,
        authenticationMethodsReference = raw.authenticationMethodsReference.getOrElse(List.empty),
        authorizedParty = raw.authorizedParty
      )
    }

}
