package me.wojnowski.oidc4s

import me.wojnowski.oidc4s.config.Location
import me.wojnowski.oidc4s.config.OpenIdConfig
import me.wojnowski.oidc4s.config.OpenIdConnectDiscovery
import me.wojnowski.oidc4s.mocks.HttpTransportMock
import me.wojnowski.oidc4s.mocks.JsonSupportMock

import cats.Id

import munit.FunSuite

class OpenIdConnectDiscoveryTest extends FunSuite {

  val configurationUrl = "https://appleid.apple.com/.well-known/openid-configuration"

  test("Happy path") {
    val expectedConfig =
      OpenIdConfig(
        Issuer("issuer"),
        "https://jwksUri"
      )
    val transport = HttpTransportMock.const[Id](configurationUrl, response = "correct-config-response")
    val jsonSupport = JsonSupportMock.instance(openIdConfigTranslations = { case "correct-config-response" =>
      Right(expectedConfig)
    })

    val discovery =
      OpenIdConnectDiscovery.instance[Id](Location.unsafeCreate("https://appleid.apple.com"))(transport, jsonSupport, Cache.noop)

    val result = discovery.getConfig

    assertEquals(result, Right(expectedConfig))
  }

  test("HTTP error") {
    val transport = HttpTransportMock.const[Id]("this-will-fail", response = "")
    val jsonSupport = JsonSupportMock.instance()

    val discovery =
      OpenIdConnectDiscovery.instance[Id](Location.unsafeCreate("https://appleid.apple.com"))(transport, jsonSupport, Cache.noop)

    val result = discovery.getConfig

    result match {
      case Left(OpenIdConnectDiscovery.Error.CouldNotFetchResponse(_)) => ()
      case _                                                           => fail("Expected an error")
    }
  }

  test("Decoding error") {
    val transport = HttpTransportMock.const[Id](configurationUrl, response = "correct-config-response")
    val jsonSupport = JsonSupportMock.instance()

    val discovery =
      OpenIdConnectDiscovery.instance[Id](Location.unsafeCreate("https://appleid.apple.com"))(transport, jsonSupport, Cache.noop)

    val result = discovery.getConfig

    result match {
      case Left(OpenIdConnectDiscovery.Error.CouldNotDecodeResponse(_)) => ()
      case _                                                            => fail("Expected an error")
    }
  }
}
