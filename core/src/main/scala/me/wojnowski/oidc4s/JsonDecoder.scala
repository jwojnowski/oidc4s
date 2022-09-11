package me.wojnowski.oidc4s

trait JsonDecoder[A] {
  def decode(raw: String): Either[String, A]
}

object JsonDecoder {
  def apply[A](implicit decoder: JsonDecoder[A]): JsonDecoder[A] = decoder
}
