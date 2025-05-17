package me.wojnowski.oidc4s.json.circe

import me.wojnowski.oidc4s.Issuer

import io.circe.Decoder

trait IssuerCirceDecoder {
  protected implicit val issuerDecoder: Decoder[Issuer] =
    Decoder[String].map(Issuer.apply)
}
