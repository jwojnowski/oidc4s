package me.wojnowski.oidc4s.mocks

import me.wojnowski.oidc4s.IdTokenClaims
import me.wojnowski.oidc4s.JoseHeader
import me.wojnowski.oidc4s.PublicKeyProvider.JsonWebKeySet
import me.wojnowski.oidc4s.config.OpenIdConfig
import me.wojnowski.oidc4s.json.JsonDecoder
import me.wojnowski.oidc4s.json.JsonSupport

object JsonSupportMock {

  def instance(
    idTokenTranslations: PartialFunction[String, Either[String, IdTokenClaims]] = PartialFunction.empty,
    joseHeaderTranslations: PartialFunction[String, Either[String, JoseHeader]] = PartialFunction.empty,
    openIdConfigTranslations: PartialFunction[String, Either[String, OpenIdConfig]] = PartialFunction.empty,
    jsonWebKeySetTranslations: PartialFunction[String, Either[String, JsonWebKeySet]] = PartialFunction.empty
  ): JsonSupport = new JsonSupport {

    override implicit val joseHeaderDecoder: JsonDecoder[JoseHeader] =
      translateOrFail(joseHeaderTranslations, "JwtHeader")

    override implicit val idTokenDecoder: JsonDecoder[IdTokenClaims] =
      translateOrFail(idTokenTranslations, "IdTokenClaims")

    override implicit def openIdConfigDecoder: JsonDecoder[OpenIdConfig] =
      translateOrFail(openIdConfigTranslations, "OpenIdConfiguration")

    override implicit val jwksDecoder: JsonDecoder[JsonWebKeySet] =
      translateOrFail(jsonWebKeySetTranslations, "JsonWebKeySet")

    private def translateOrFail[A](translations: PartialFunction[String, Either[String, A]], name: String): JsonDecoder[A] =
      (rawJson: String) => translations.lift(rawJson.trim).toRight(s"could not find $name for [$rawJson]").flatten
  }

}
