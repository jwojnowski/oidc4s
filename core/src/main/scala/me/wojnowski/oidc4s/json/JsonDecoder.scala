package me.wojnowski.oidc4s.json

import me.wojnowski.oidc4s.IdTokenClaims

trait JsonDecoder[A] {
  def decode(raw: String): Either[String, A]
}

object JsonDecoder {
  def apply[A](implicit decoder: JsonDecoder[A]): JsonDecoder[A] = decoder

  /** Hint: implemented as part of specific JsonSupport implementations */
  type ClaimsDecoder[A] = JsonDecoder[(A, IdTokenClaims)]

  object ClaimsDecoder {
    def apply[A](implicit decoder: ClaimsDecoder[A]): ClaimsDecoder[A] = decoder
  }

}
