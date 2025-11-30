package me.wojnowski.oidc4s.json.circe

import me.wojnowski.oidc4s.json.JsonSupport
import me.wojnowski.oidc4s.json.JwksJsonSupportTest

class JwksCirceJsonSupportTest extends JwksJsonSupportTest {
  override def jsonSupport: JsonSupport = CirceJsonSupport
}
