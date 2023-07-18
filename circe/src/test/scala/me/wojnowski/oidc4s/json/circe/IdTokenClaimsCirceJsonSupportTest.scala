package me.wojnowski.oidc4s.json.circe

import cats.data.NonEmptySet
import io.circe.Decoder
import me.wojnowski.oidc4s.IdTokenClaims._
import me.wojnowski.oidc4s.IdTokenClaims
import me.wojnowski.oidc4s.Issuer
import me.wojnowski.oidc4s.json.JsonDecoder.ClaimsDecoder
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
          issuer = Issuer("https://openid.c2id.com"),
          subject = Subject("alice"),
          audience = NonEmptySet.of(Audience("client-12345")),
          expiration = Instant.parse("2011-07-21T20:59:30Z"),
          issuedAt = Instant.parse("2011-07-21T20:42:50Z"),
          authenticationTime = Some(Instant.parse("2011-07-21T20:42:49Z")),
          nonce = Some(Nonce("n-0S6_WzA2Mj")),
          authenticationContextClassReference = Some(AuthenticationContextClassReference("c2id.loa.hisec")),
          authenticationMethodsReference = List(
            AuthenticationMethodReference("pwd"),
            AuthenticationMethodReference("rsa"),
            AuthenticationMethodReference("mfa")
          ),
          authorizedParty = Some(AuthorizedParty("client-00001"))
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
          issuer = Issuer("https://openid.c2id.com"),
          subject = Subject("alice"),
          audience = NonEmptySet.of(Audience("client-12345")),
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
          issuer = Issuer("https://openid.c2id.com"),
          subject = Subject("alice"),
          audience = NonEmptySet.of(Audience("client-12345"), Audience("client-54321")),
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

  test("Custom claims (with Issuer) decoding") {
    import CirceJsonSupport._

    case class CustomClaims(foo: String, bar: Int)
    implicit val decoder: Decoder[CustomClaims] = Decoder.forProduct2[CustomClaims, String, Int]("foo", "bar")(CustomClaims.apply)

    val rawJson =
      """{"foo": "Foo", "bar": 12, "additionalField": "doesn't matter", "iss": "https://example.com", "sub": "a-subject", "aud": "audience", "exp": 3, "iat": 0}"""

    val result = ClaimsDecoder[CustomClaims].decode(rawJson)

    assertEquals(
      result,
      Right(
        (
          CustomClaims(foo = "Foo", bar = 12),
          IdTokenClaims(
            Issuer("https://example.com"),
            Subject("a-subject"),
            NonEmptySet.of(Audience("audience")),
            expiration = Instant.EPOCH.plusSeconds(3),
            issuedAt = Instant.EPOCH
          )
        )
      )
    )
  }
}
