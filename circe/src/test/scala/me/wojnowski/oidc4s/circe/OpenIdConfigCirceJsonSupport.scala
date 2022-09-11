package me.wojnowski.oidc4s.circe

import me.wojnowski.oidc4s.OpenIdConfig
import munit.FunSuite

import java.nio.file.Files
import java.nio.file.Paths
import scala.io.Source

class OpenIdConfigCirceJsonSupport extends FunSuite {
  test("Correct config") {
    val rawJson = Source.fromResource("validOpenIdConfiguration.json").getLines().mkString("\n")

    val result = CirceJsonSupport.openIdConfigDecoder.decode(rawJson)

    assertEquals(
      result,
      expected = Right(
        OpenIdConfig(
          issuer = "https://appleid.apple.com",
          jwksUri = "https://appleid.apple.com/auth/keys"
        )
      )
    )
  }

  test("Invalid json") {
    val rawInvalidJson = """{"foo: "bar""""

    val result = CirceJsonSupport.openIdConfigDecoder.decode(rawInvalidJson)

    assert(result.isLeft)
  }

  test("Missing field(s)") {
    val rawJson = """{"foo": "bar"}"""

    val result = CirceJsonSupport.openIdConfigDecoder.decode(rawJson)

    assert(result.isLeft)
  }
}
