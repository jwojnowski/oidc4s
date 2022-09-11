package me.wojnowski.oidc4s

import java.time.Instant

case class IdTokenClaims(
  issuer: String,
  subject: String,
  audience: Set[String],
  expiration: Instant,
  issuedAt: Instant,
  authenticationTime: Option[Instant] = None,
  nonce: Option[String] = None,
  authenticationContextClassReference: Option[String] = None,
  authenticationMethodsReference: List[String] = List.empty,
  authorizedParty: Option[String] = None
)
