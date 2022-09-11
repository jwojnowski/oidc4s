package me.wojnowski.oidc4s.circe

import me.wojnowski.oidc4s.IdTokenClaims
import munit.FunSuite

import java.time.Instant
import scala.io.Source

class IdTokenClaimsCirceJsonSupportTest extends FunSuite {
  test("Single-audience, full token") {
    val rawJson = Source.fromResource("fullIdTokenWithSingleAudience.json").getLines().mkString("\n")

    val result = CirceJsonSupport.idTokenDecoder.decode(rawJson)

    assertEquals(
      result,
      Right(
        IdTokenClaims(
          issuer = "https://openid.c2id.com",
          subject = "alice",
          audience = Set("client-12345"),
          expiration = Instant.parse("2011-07-21T20:59:30Z"),
          issuedAt = Instant.parse("2011-07-21T20:42:50Z"),
          authenticationTime = Some(Instant.parse("2011-07-21T20:42:49Z")),
          nonce = Some("n-0S6_WzA2Mj"),
          authenticationContextClassReference = Some("c2id.loa.hisec"),
          authenticationMethodsReference = List("pwd", "rsa", "mfa"),
          authorizedParty = Some("client-00001")
        )
      )
    )

  }

  test("Single-audience, minimal token") {
    val rawJson = Source.fromResource("minIdTokenWithSingleAudience.json").getLines().mkString("\n")

    val result = CirceJsonSupport.idTokenDecoder.decode(rawJson)

    assertEquals(
      result,
      Right(
        IdTokenClaims(
          issuer = "https://openid.c2id.com",
          subject = "alice",
          audience = Set("client-12345"),
          expiration = Instant.parse("2011-07-21T20:59:30Z"),
          issuedAt = Instant.parse("2011-07-21T20:42:50Z")
        )
      )
    )

  }

  test("Multi-audience, minimal token") {
    val rawJson = Source.fromResource("minIdTokenWithMultipleAudiences.json").getLines().mkString("\n")

    val result = CirceJsonSupport.idTokenDecoder.decode(rawJson)

    assertEquals(
      result,
      Right(
        IdTokenClaims(
          issuer = "https://openid.c2id.com",
          subject = "alice",
          audience = Set("client-12345", "client-54321"),
          expiration = Instant.parse("2011-07-21T20:59:30Z"),
          issuedAt = Instant.parse("2011-07-21T20:42:50Z")
        )
      )
    )

  }

  test("IdToken decoding (missing field)") {
    val rawJson = """{"foo": "bar"}"""

    val result = CirceJsonSupport.idTokenDecoder.decode(rawJson)

    assert(result.isLeft)
  }

  test("IdToken decoding (invalid JSON)") {
    val rawJson = """{"foo": bar"""

    val result = CirceJsonSupport.idTokenDecoder.decode(rawJson)

    assert(result.isLeft)
  }
}
