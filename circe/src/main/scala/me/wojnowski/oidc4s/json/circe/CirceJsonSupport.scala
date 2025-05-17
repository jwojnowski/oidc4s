package me.wojnowski.oidc4s.json.circe

import me.wojnowski.oidc4s.IdTokenClaims
import me.wojnowski.oidc4s.JoseHeader
import me.wojnowski.oidc4s.PublicKeyProvider
import me.wojnowski.oidc4s.config.OpenIdConfig
import me.wojnowski.oidc4s.json.JsonDecoder
import me.wojnowski.oidc4s.json.JsonDecoder.ClaimsDecoder
import me.wojnowski.oidc4s.json.JsonSupport

import cats.syntax.all._

import io.circe.Decoder
import io.circe.parser

trait CirceJsonSupport
  extends JsonSupport
  with IdTokenCirceDecoder
  with JoseHeaderCirceDecoder
  with OpenIdConfigurationCirceDecoder
  with JsonWebKeySetCirceDecoder {

  override implicit val joseHeaderDecoder: JsonDecoder[JoseHeader] = fromCirce

  override implicit val idTokenDecoder: JsonDecoder[IdTokenClaims] = fromCirce

  override implicit val openIdConfigDecoder: JsonDecoder[OpenIdConfig] = fromCirce

  override implicit val jwksDecoder: JsonDecoder[PublicKeyProvider.JsonWebKeySet] = fromCirce

  implicit def claimsDecoder[A: Decoder]: ClaimsDecoder[A] = fromCirce

  private def fromCirce[A: Decoder]: JsonDecoder[A] =
    (raw: String) => parser.decode[A](raw).leftMap(_.getMessage)

  private implicit def issuerAudienceAndCirceDecoder[A: Decoder]: Decoder[(A, IdTokenClaims)] =
    Decoder.instance { hCursor =>
      (
        Decoder[A].tryDecode(hCursor),
        Decoder[IdTokenClaims].tryDecode(hCursor)
      ).tupled
    }

}

object CirceJsonSupport extends CirceJsonSupport
