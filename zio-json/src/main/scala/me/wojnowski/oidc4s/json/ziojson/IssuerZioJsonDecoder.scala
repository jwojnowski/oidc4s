package me.wojnowski.oidc4s.json.ziojson

import me.wojnowski.oidc4s.Issuer

import zio.json._

trait IssuerZioJsonDecoder {
  protected implicit val issuerDecoder: JsonDecoder[Issuer] =
    JsonDecoder[String].map(Issuer.apply)
}
