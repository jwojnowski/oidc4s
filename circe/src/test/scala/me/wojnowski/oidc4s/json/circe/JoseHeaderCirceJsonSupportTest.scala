package me.wojnowski.oidc4s.json.circe

import me.wojnowski.oidc4s.json.JoseHeaderJsonSupportTest
import me.wojnowski.oidc4s.json.JsonSupport

class JoseHeaderCirceJsonSupportTest extends JoseHeaderJsonSupportTest {
  override def jsonSupport: JsonSupport = CirceJsonSupport
}
