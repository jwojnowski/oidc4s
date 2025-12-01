package me.wojnowski.oidc4s.json.ziojson

import me.wojnowski.oidc4s.json.JoseHeaderJsonSupportTest
import me.wojnowski.oidc4s.json.JsonSupport

class JoseHeaderZioJsonSupportTest extends JoseHeaderJsonSupportTest {
  override def jsonSupport: JsonSupport = ZioJsonSupport
}
