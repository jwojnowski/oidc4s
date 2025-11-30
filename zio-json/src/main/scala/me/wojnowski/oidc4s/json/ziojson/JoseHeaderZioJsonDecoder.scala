package me.wojnowski.oidc4s.json.ziojson

import me.wojnowski.oidc4s.Algorithm
import me.wojnowski.oidc4s.IdTokenVerifier.Error.UnsupportedAlgorithm
import me.wojnowski.oidc4s.JoseHeader

import zio.json._

trait JoseHeaderZioJsonDecoder {

  private implicit val algorithmDecoder: JsonDecoder[Algorithm] =
    JsonDecoder[String].mapOrFail { shortName =>
      Algorithm.findByShortName(shortName).toRight(UnsupportedAlgorithm(shortName).toRawError)
    }

  private case class JoseHeaderRaw(
    @jsonField("kid") keyId: String,
    @jsonField("alg") algorithm: Algorithm
  )

  private object JoseHeaderRaw {
    implicit val decoder: JsonDecoder[JoseHeaderRaw] =
      DeriveJsonDecoder.gen[JoseHeaderRaw]
  }

  protected implicit val joseHeaderZioJsonDecoder: JsonDecoder[JoseHeader] =
    JoseHeaderRaw.decoder.map(raw => JoseHeader(raw.keyId, raw.algorithm))
}
