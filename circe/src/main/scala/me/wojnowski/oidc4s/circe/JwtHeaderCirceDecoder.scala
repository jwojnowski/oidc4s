package me.wojnowski.oidc4s.circe

import io.circe.Decoder
import pdi.jwt.JwtHeader

trait JwtHeaderCirceDecoder {
  protected implicit val jwtHeaderCirceDecoder: Decoder[JwtHeader] =
    Decoder.forProduct1[JwtHeader, String]("kid") { kid =>
      JwtHeader(keyId = Some(kid))
    }
}
