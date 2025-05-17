package me.wojnowski.oidc4s.json.circe

import me.wojnowski.oidc4s.Algorithm
import me.wojnowski.oidc4s.JoseHeader
import me.wojnowski.oidc4s.json.circe.CirceJsonSupport

import munit.FunSuite

class JoseHeaderCirceJsonSupportTest extends FunSuite {
  test("JoseHeader is decoding") {
    // language=JSON
    val rawJson = """{"kid":"thisiskeyid","alg":"RS256"}"""

    val result = CirceJsonSupport.joseHeaderDecoder.decode(rawJson)

    assertEquals(result, Right(JoseHeader(keyId = "thisiskeyid", algorithm = Algorithm.Rs256)))
  }

  test("JoseHeader decoding (missing field)") {
    val rawJson = """{"alg": "RS256"}"""

    val result = CirceJsonSupport.joseHeaderDecoder.decode(rawJson)

    assert(result.isLeft)
  }

  test("JoseHeader decoding (invalid JSON)") {
    val rawJson = """{"kid": "thisiskeyid", "alg": "RS256""""

    val result = CirceJsonSupport.joseHeaderDecoder.decode(rawJson)

    assert(result.isLeft)
  }
}
