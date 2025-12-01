package me.wojnowski.oidc4s.json.ziojson

import me.wojnowski.oidc4s.IdTokenClaims
import me.wojnowski.oidc4s.IdTokenClaims._
import me.wojnowski.oidc4s.Issuer
import me.wojnowski.oidc4s.json.IdTokenClaimsJsonSupportTest
import me.wojnowski.oidc4s.json.JsonDecoder.ClaimsDecoder
import me.wojnowski.oidc4s.json.JsonSupport

import cats.data.NonEmptySet

import java.time.Instant

import zio.json.DeriveJsonDecoder
import zio.json.JsonDecoder

class IdTokenClaimsZioJsonSupportTest extends IdTokenClaimsJsonSupportTest {
  override def jsonSupport: JsonSupport = ZioJsonSupport

  test("Custom claims (with Issuer) decoding") {
    import ZioJsonSupport._

    case class CustomClaims(foo: String, bar: Int)
    implicit val decoder: JsonDecoder[CustomClaims] = DeriveJsonDecoder.gen[CustomClaims]

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
