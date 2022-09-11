package me.wojnowski.oidc4s

import me.wojnowski.oidc4s.PublicKeyProvider.JsonWebKeySet
import pdi.jwt.JwtHeader

object JsonSupportMock {

  def instance(
    idTokenTranslations: PartialFunction[String, IdTokenClaims] = PartialFunction.empty,
    jwtHeaderTranslations: PartialFunction[String, JwtHeader] = PartialFunction.empty,
    openIdConfigTranslations: PartialFunction[String, OpenIdConfig] = PartialFunction.empty,
    jsonWebKeySetTranslations: PartialFunction[String, JsonWebKeySet] = PartialFunction.empty
  ): JsonSupport = new JsonSupport {

    override implicit val jwtHeaderDecoder: JsonDecoder[JwtHeader] =
      translateOrFail(jwtHeaderTranslations, "JwtHeader")

    override implicit val idTokenDecoder: JsonDecoder[IdTokenClaims] =
      translateOrFail(idTokenTranslations, "IdTokenClaims")

    override implicit def openIdConfigDecoder: JsonDecoder[OpenIdConfig] =
      translateOrFail(openIdConfigTranslations, "OpenIdConfiguration")

    override implicit val jwksDecoder: JsonDecoder[JsonWebKeySet] =
      translateOrFail(jsonWebKeySetTranslations, "JsonWebKeySet")

    private def translateOrFail[A](translations: PartialFunction[String, A], name: String): JsonDecoder[A] =
      (rawJson: String) => translations.lift(rawJson.trim).toRight(s"could not find $name for [$rawJson]")
  }

}
