package me.wojnowski.oidc4s.json.circe

import me.wojnowski.oidc4s.json.JsonSupport
import me.wojnowski.oidc4s.json.OpenIdConfigJsonSupportTest

class OpenIdConfigCirceJsonSupportTest extends OpenIdConfigJsonSupportTest {
  override def jsonSupport: JsonSupport = CirceJsonSupport
}
