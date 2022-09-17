package me.wojnowski.oidc4s.circe

import io.circe.Decoder
import me.wojnowski.oidc4s.Issuer
import me.wojnowski.oidc4s.OpenIdConfig

trait OpenIdConfigurationCirceDecoder {
  private implicit val issuerDecoder: Decoder[Issuer] =
    Decoder[String].map(Issuer.apply)

  protected implicit val openIdConfigurationDecoder: Decoder[OpenIdConfig] =
    Decoder.forProduct2[OpenIdConfig, Issuer, String]("issuer", "jwks_uri")(OpenIdConfig.apply)
}
