package me.wojnowski.oidc4s.json.circe

import me.wojnowski.oidc4s.Algorithm
import me.wojnowski.oidc4s.JwtHeader
import me.wojnowski.oidc4s.json.circe.CirceJsonSupport
import munit.FunSuite

class JwtHeaderCirceJsonSupportTest extends FunSuite {
  test("JwtHeader is decoding") {
    // language=JSON
    val rawJson = """{"kid":"thisiskeyid","alg":"RS256"}"""

    val result = CirceJsonSupport.jwtHeaderDecoder.decode(rawJson)

    assertEquals(result, Right(JwtHeader(keyId = "thisiskeyid", algorithm = Algorithm.Rs256)))
  }

  test("JwtHeader decoding (missing field)") {
    val rawJson = """{"alg": "RS256"}"""

    val result = CirceJsonSupport.jwtHeaderDecoder.decode(rawJson)

    assert(result.isLeft)
  }

  test("JwtHeader decoding (invalid JSON)") {
    val rawJson = """{"kid": "thisiskeyid", "alg": "RS256""""

    val result = CirceJsonSupport.jwtHeaderDecoder.decode(rawJson)

    assert(result.isLeft)
  }
}
