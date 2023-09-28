package me.wojnowski.oidc4s.json

import me.wojnowski.oidc4s.IdTokenClaims
import me.wojnowski.oidc4s.JwtHeader
import me.wojnowski.oidc4s.PublicKeyProvider.JsonWebKeySet
import me.wojnowski.oidc4s.config.OpenIdConfig

trait JsonSupport {
  implicit def jwtHeaderDecoder: JsonDecoder[JwtHeader]
  implicit def idTokenDecoder: JsonDecoder[IdTokenClaims]
  implicit def openIdConfigDecoder: JsonDecoder[OpenIdConfig]
  implicit def jwksDecoder: JsonDecoder[JsonWebKeySet]
}
