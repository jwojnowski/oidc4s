package me.wojnowski.oidc4s.json.circe

import io.circe.Decoder
import me.wojnowski.oidc4s.Algorithm
import me.wojnowski.oidc4s.JoseHeader

trait JoseHeaderCirceDecoder {

  private implicit val jwtAlgorithmCirceDecoder: Decoder[Algorithm] =
    Decoder[String].map(Algorithm.fromString)

  protected implicit val joseHeaderCirceDecoder: Decoder[JoseHeader] =
    Decoder.forProduct2[JoseHeader, String, Algorithm]("kid", "alg")(JoseHeader.apply)

}
