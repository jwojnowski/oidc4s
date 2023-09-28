package me.wojnowski.oidc4s.json.circe

import io.circe.Decoder
import me.wojnowski.oidc4s.Algorithm
import me.wojnowski.oidc4s.JwtHeader

trait JwtHeaderCirceDecoder {

  private implicit val jwtAlgorithmCirceDecoder: Decoder[Algorithm] =
    Decoder[String].map(Algorithm.fromString)

  protected implicit val jwtHeaderCirceDecoder: Decoder[JwtHeader] =
    Decoder.forProduct2[JwtHeader, String, Algorithm]("kid", "alg")(JwtHeader.apply)

}
