package me.wojnowski.oidc4s.json.circe

import me.wojnowski.oids4s.json.circe.CirceJsonSupport
import munit.FunSuite
import pdi.jwt.JwtHeader

class JwtHeaderCirceJsonSupportTest extends FunSuite {
  test("JwtHeader is decoding") {
    //language=JSON
    val rawJson = """{"kid":"thisiskeyid","alg":"RS256"}"""

    val result = CirceJsonSupport.jwtHeaderDecoder.decode(rawJson)

    assertEquals(result, Right(JwtHeader(keyId = Some("thisiskeyid"))))
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
