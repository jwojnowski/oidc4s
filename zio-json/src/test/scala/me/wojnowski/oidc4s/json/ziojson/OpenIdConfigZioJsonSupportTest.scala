package me.wojnowski.oidc4s.json.ziojson

import me.wojnowski.oidc4s.json.JsonSupport
import me.wojnowski.oidc4s.json.OpenIdConfigJsonSupportTest

class OpenIdConfigZioJsonSupportTest extends OpenIdConfigJsonSupportTest {
  override def jsonSupport: JsonSupport = ZioJsonSupport
}
