package me.wojnowski.oidc4s.json.circe

import io.circe.Decoder
import me.wojnowski.oidc4s.Issuer

trait IssuerCirceDecoder {
  protected implicit val issuerDecoder: Decoder[Issuer] =
    Decoder[String].map(Issuer.apply)
}
