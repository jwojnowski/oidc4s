package me.wojnowski.oidc4s.json.ziojson

import me.wojnowski.oidc4s.json.JsonSupport
import me.wojnowski.oidc4s.json.JwksJsonSupportTest

class JwksZioJsonSupportTest extends JwksJsonSupportTest {
  override def jsonSupport: JsonSupport = ZioJsonSupport
}
