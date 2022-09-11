package me.wojnowski.oidc4s.circe

import io.circe.Decoder
import me.wojnowski.oidc4s.OpenIdConfig

trait OpenIdConfigurationCirceDecoder {
  protected implicit val openIdConfigurationDecoder: Decoder[OpenIdConfig] =
    Decoder.forProduct2[OpenIdConfig, String, String]("issuer", "jwks_uri")(OpenIdConfig.apply)
}
