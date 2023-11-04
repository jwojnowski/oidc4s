package me.wojnowski.oidc4s.json.circe

import io.circe.Decoder
import me.wojnowski.oidc4s.Algorithm
import me.wojnowski.oidc4s.IdTokenVerifier.Error.UnsupportedAlgorithm
import me.wojnowski.oidc4s.JoseHeader
import me.wojnowski.oidc4s.json.JsonSupport

trait JoseHeaderCirceDecoder {

  private implicit val jwtAlgorithmCirceDecoder: Decoder[Algorithm] =
    Decoder[String].emap { shortName =>
      Algorithm.findByShortName(shortName).toRight(UnsupportedAlgorithm(shortName).toRawError)
    }

  protected implicit val joseHeaderCirceDecoder: Decoder[JoseHeader] =
    Decoder.forProduct2[JoseHeader, String, Algorithm]("kid", "alg")(JoseHeader.apply)

}
