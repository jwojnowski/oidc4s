package me.wojnowski.oidc4s.json.ziojson

import me.wojnowski.oidc4s.Issuer
import me.wojnowski.oidc4s.config.OpenIdConfig

import zio.json._

trait OpenIdConfigurationZioJsonDecoder {
  private implicit val issuerDecoder: JsonDecoder[Issuer] =
    JsonDecoder[String].map(Issuer.apply)

  @jsonMemberNames(SnakeCase)
  private case class OpenIdConfigRaw(
    issuer: Issuer,
    jwksUri: String
  )

  private object OpenIdConfigRaw {
    implicit val decoder: JsonDecoder[OpenIdConfigRaw] =
      DeriveJsonDecoder.gen[OpenIdConfigRaw]
  }

  protected implicit val openIdConfigZioJsonDecoder: JsonDecoder[OpenIdConfig] =
    OpenIdConfigRaw.decoder.map(raw => OpenIdConfig(raw.issuer, raw.jwksUri))
}
