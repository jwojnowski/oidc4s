package me.wojnowski.oidc4s

import me.wojnowski.oidc4s.IdTokenClaims._
import munit.FunSuite

import java.time.Instant

class IdTokenClaimsTest extends FunSuite {
  private val clientId = ClientId("client-id")
  private val issuedAt = Instant.EPOCH
  private val expiresIn = Instant.EPOCH.plusSeconds(30)
  private val issuer = Issuer("issuer")
  private val subject = Subject("subject")
  private val otherAudience = Audience("other-audience")

  test("Authorized Party matches Client ID and it's present in Audience") {
    val claims = createClaims(
      authorizedParty = Some(AuthorizedParty(clientId.value)),
      audience = Set(Audience(clientId.value), otherAudience)
    )

    val result = claims.matchesClientId(clientId)

    assertEquals(result, true)
  }

  test("Authorized Party doesn't match Client ID, but Client ID present in Audience") {
    val claims = createClaims(
      authorizedParty = Some(AuthorizedParty("different-value")),
      audience = Set(Audience(clientId.value), otherAudience)
    )

    val result = claims.matchesClientId(clientId)

    assertEquals(result, false)
  }

  // According to https://bitbucket.org/openid/connect/issues/973/ this should NOT be allowed
  // but there are contradictions in RFC, and according to
  // https://stackoverflow.com/questions/41231018/openid-connect-standard-authorized-party-azp-contradiction
  // Google actually issues tokens without azp in aud
  test("Authorized Party matches Client ID, but it's not present in Audience") {
    val claims = createClaims(
      authorizedParty = Some(AuthorizedParty(clientId.value)),
      audience = Set(otherAudience)
    )

    val result = claims.matchesClientId(clientId)

    assertEquals(result, true)
  }

  test("No Authorized Party, Client ID present in Audience") {
    val claims = createClaims(
      authorizedParty = None,
      audience = Set(Audience(clientId.value), otherAudience)
    )

    val result = claims.matchesClientId(clientId)

    assertEquals(result, true)
  }

  test("No Authorized Party, Client ID not present in Audience") {
    val claims = createClaims(
      authorizedParty = None,
      audience = Set(otherAudience)
    )

    val result = claims.matchesClientId(clientId)

    assertEquals(result, false)
  }

  private def createClaims(authorizedParty: Option[AuthorizedParty], audience: Set[Audience]): IdTokenClaims =
    IdTokenClaims(issuer, subject, audience, expiresIn, issuedAt, authorizedParty = authorizedParty)
}
