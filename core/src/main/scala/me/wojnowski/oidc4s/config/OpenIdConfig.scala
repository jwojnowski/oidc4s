package me.wojnowski.oidc4s.config

import me.wojnowski.oidc4s.Issuer

case class OpenIdConfig(
  issuer: Issuer,
  jwksUri: String
)
