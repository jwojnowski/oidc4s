package me.wojnowski.oidc4s

import me.wojnowski.oidc4s.PublicKeyProvider.Error
import me.wojnowski.oidc4s.PublicKeyProvider.Error.CouldNotDecodePublicKey

import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import scala.util.Try
import cats.implicits._

object KeyUtils {
  private val keyFactory = KeyFactory.getInstance("RSA")

  def parsePublicPemKey(raw: String): Either[Error.CouldNotDecodePublicKey, PublicKey] = Try {
    val dataString =
      raw
        .replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")
        .replaceAll("\\s", "")

    val keySpec = new X509EncodedKeySpec(Base64.getDecoder.decode(dataString))
    keyFactory.generatePublic(keySpec)
  }.toEither.leftMap(CouldNotDecodePublicKey.apply)

}
