package me.wojnowski.oidc4s.json

import me.wojnowski.oidc4s.IdTokenClaims
import me.wojnowski.oidc4s.JoseHeader
import me.wojnowski.oidc4s.PublicKeyProvider.JsonWebKeySet
import me.wojnowski.oidc4s.config.OpenIdConfig

trait JsonSupport {
  implicit def joseHeaderDecoder: JsonDecoder[JoseHeader]
  implicit def idTokenDecoder: JsonDecoder[IdTokenClaims]
  implicit def openIdConfigDecoder: JsonDecoder[OpenIdConfig]
  implicit def jwksDecoder: JsonDecoder[JsonWebKeySet]
}

object JsonSupport {
  private[oidc4s] val unsupportedAlgorithmErrorPrefix = "Unsupported algorithm: "
}
