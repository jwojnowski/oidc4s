package me.wojnowski.oids4s.json.circe

import cats.syntax.all._
import io.circe.Decoder
import io.circe.parser
import me.wojnowski.oidc4s.IdTokenClaims
import me.wojnowski.oidc4s.PublicKeyProvider
import me.wojnowski.oidc4s.config.OpenIdConfig
import me.wojnowski.oidc4s.json.JsonDecoder
import me.wojnowski.oidc4s.json.JsonSupport
import pdi.jwt.JwtHeader

trait CirceJsonSupport
  extends JsonSupport
  with IdTokenCirceDecoder
  with JwtHeaderCirceDecoder
  with OpenIdConfigurationCirceDecoder
  with JsonWebKeySetCirceDecoder {

  override implicit val jwtHeaderDecoder: JsonDecoder[JwtHeader] = fromCirce

  override implicit val idTokenDecoder: JsonDecoder[IdTokenClaims] = fromCirce

  override implicit val openIdConfigDecoder: JsonDecoder[OpenIdConfig] = fromCirce

  override implicit val jwksDecoder: JsonDecoder[PublicKeyProvider.JsonWebKeySet] = fromCirce

  private def fromCirce[A: Decoder]: JsonDecoder[A] =
    (raw: String) => parser.decode[A](raw).leftMap(_.getMessage)

}

object CirceJsonSupport extends CirceJsonSupport
