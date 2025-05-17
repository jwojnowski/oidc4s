package me.wojnowski.oidc4s.json.circe

import me.wojnowski.oidc4s.PublicKeyProvider.JsonWebKey
import me.wojnowski.oidc4s.PublicKeyProvider.JsonWebKeySet
import me.wojnowski.oidc4s.json.circe.CirceJsonSupport

import munit.FunSuite

class JwksCirceJsonSupportTest extends FunSuite {
  test("JsonWebKeySet decoding") {
    // language=JSON
    val rawJson =
      """
         {
           "keys": [
             {
               "kid": "kid1",
               "n": "n1",
               "e": "e1"
             },
             {
               "kid": "kid2",
               "n": "n2",
               "e": "e2"
             }
           ]
         }
        """

    val result = CirceJsonSupport.jwksDecoder.decode(rawJson)

    assertEquals(
      result,
      Right(
        JsonWebKeySet(
          List(
            JsonWebKey(
              modulus = "n1",
              publicExponent = "e1",
              keyId = "kid1"
            ),
            JsonWebKey(
              modulus = "n2",
              publicExponent = "e2",
              keyId = "kid2"
            )
          )
        )
      )
    )
  }

  test("Empty JsonWebKeySet decoding") {
    val rawJson = """{"keys": []}"""

    val result = CirceJsonSupport.jwksDecoder.decode(rawJson)

    assertEquals(result, Right(JsonWebKeySet(List.empty)))
  }

  test("Invalid JsonWebKey") {
    val rawJson =
      """{"keys": [{"kid":"valid-json","n":"invalidkeyitself","e":"butitdoesntmatternow"}, {"kid":"butthisonesnotvalidatall"}]}"""

    val result = CirceJsonSupport.jwksDecoder.decode(rawJson)

    assert(result.isLeft)
  }

  test("JsonWebKeySet decoding (missing field)") {
    val rawJson = """{"foo": "bar"}"""

    val result = CirceJsonSupport.jwksDecoder.decode(rawJson)

    assert(result.isLeft)
  }

  test("JsonWebKeySet decoding (invalid JSON)") {
    val rawJson = """{"foo": "bar"""

    val result = CirceJsonSupport.jwksDecoder.decode(rawJson)

    assert(result.isLeft)
  }
}
