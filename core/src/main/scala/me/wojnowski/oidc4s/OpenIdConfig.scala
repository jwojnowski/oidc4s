package me.wojnowski.oidc4s

case class OpenIdConfig(
  issuer: Issuer,
  jwksUri: String
)
