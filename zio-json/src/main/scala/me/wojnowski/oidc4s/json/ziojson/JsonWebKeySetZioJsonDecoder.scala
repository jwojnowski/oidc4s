package me.wojnowski.oidc4s.json.ziojson

import me.wojnowski.oidc4s.PublicKeyProvider.JsonWebKey
import me.wojnowski.oidc4s.PublicKeyProvider.JsonWebKeySet
import me.wojnowski.oidc4s.PublicKeyProvider.KeyId

import zio.json._

trait JsonWebKeySetZioJsonDecoder {

  private case class JsonWebKeyRaw(
    @jsonField("n") modulus: String,
    @jsonField("e") publicExponent: String,
    @jsonField("kid") keyId: KeyId
  )

  private object JsonWebKeyRaw {
    implicit val decoder: JsonDecoder[JsonWebKeyRaw] =
      DeriveJsonDecoder.gen[JsonWebKeyRaw]
  }

  private implicit val jsonWebKeyDecoder: JsonDecoder[JsonWebKey] =
    JsonWebKeyRaw.decoder.map(raw => JsonWebKey(raw.modulus, raw.publicExponent, raw.keyId))

  private case class JsonWebKeySetRaw(
    @jsonField("keys") keys: List[JsonWebKey]
  )

  private object JsonWebKeySetRaw {
    implicit val decoder: JsonDecoder[JsonWebKeySetRaw] =
      DeriveJsonDecoder.gen[JsonWebKeySetRaw]
  }

  protected implicit val jsonWebKeySetZioJsonDecoder: JsonDecoder[JsonWebKeySet] =
    JsonWebKeySetRaw.decoder.map(raw => JsonWebKeySet(raw.keys))
}
