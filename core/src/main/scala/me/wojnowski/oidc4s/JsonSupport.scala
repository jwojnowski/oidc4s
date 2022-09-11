package me.wojnowski.oidc4s

import me.wojnowski.oidc4s.PublicKeyProvider.JsonWebKeySet
import pdi.jwt.JwtHeader

trait JsonSupport {
  implicit def jwtHeaderDecoder: JsonDecoder[JwtHeader]
  implicit def idTokenDecoder: JsonDecoder[IdTokenClaims]
  implicit def openIdConfigDecoder: JsonDecoder[OpenIdConfig]
  implicit def jwksDecoder: JsonDecoder[JsonWebKeySet]
}
