package me.wojnowski.oidc4s.json

import me.wojnowski.oidc4s.Issuer
import me.wojnowski.oidc4s.config

import scala.io.Source

import munit.FunSuite

trait OpenIdConfigJsonSupportTest extends FunSuite {
  def jsonSupport: JsonSupport

  test("Correct config") {
    val rawJson = Source.fromResource("validOpenIdConfiguration.json").getLines().mkString("\n")

    val result = jsonSupport.openIdConfigDecoder.decode(rawJson)

    assertEquals(
      result,
      expected = Right(
        config.OpenIdConfig(
          issuer = Issuer("https://appleid.apple.com"),
          jwksUri = "https://appleid.apple.com/auth/keys"
        )
      )
    )
  }

  test("Invalid json") {
    val rawInvalidJson = """{"foo: "bar""""

    val result = jsonSupport.openIdConfigDecoder.decode(rawInvalidJson)

    assert(result.isLeft)
  }

  test("Missing field(s)") {
    val rawJson = """{"foo": "bar"}"""

    val result = jsonSupport.openIdConfigDecoder.decode(rawJson)

    assert(result.isLeft)
  }
}
