package me.wojnowski.oidc4s.json.ziojson

import me.wojnowski.oidc4s.IdTokenClaims
import me.wojnowski.oidc4s.JoseHeader
import me.wojnowski.oidc4s.PublicKeyProvider
import me.wojnowski.oidc4s.config.OpenIdConfig
import me.wojnowski.oidc4s.json.JsonDecoder
import me.wojnowski.oidc4s.json.JsonDecoder.ClaimsDecoder
import me.wojnowski.oidc4s.json.JsonSupport

import zio.json.ast.Json

trait ZioJsonSupport
  extends JsonSupport
  with IdTokenZioJsonDecoder
  with JoseHeaderZioJsonDecoder
  with OpenIdConfigurationZioJsonDecoder
  with JsonWebKeySetZioJsonDecoder {

  override implicit val joseHeaderDecoder: JsonDecoder[JoseHeader] = fromZioJson

  override implicit val idTokenDecoder: JsonDecoder[IdTokenClaims] = fromZioJson

  override implicit val openIdConfigDecoder: JsonDecoder[OpenIdConfig] = fromZioJson

  override implicit val jwksDecoder: JsonDecoder[PublicKeyProvider.JsonWebKeySet] = fromZioJson

  implicit def claimsDecoder[A: zio.json.JsonDecoder]: ClaimsDecoder[A] = fromZioJson

  private def fromZioJson[A: zio.json.JsonDecoder]: JsonDecoder[A] =
    (raw: String) => zio.json.JsonDecoder[A].decodeJson(raw)

  private implicit def customAndIdTokenClaimsDecoder[A: zio.json.JsonDecoder]: zio.json.JsonDecoder[(A, IdTokenClaims)] =
    zio.json.JsonDecoder[Json].mapOrFail { json =>
      for {
        a      <- json.as[A].left.map(error => s"Could not decode custom claims: $error")
        claims <- json.as[IdTokenClaims].left.map(error => s"Could not decode IdTokenClaims: $error")
      } yield (a, claims)
    }

}

object ZioJsonSupport extends ZioJsonSupport
