package me.wojnowski.oidc4s.transport

import me.wojnowski.oidc4s.ProductSerializableNoStackTrace
import me.wojnowski.oidc4s.transport.Transport.Response

import scala.concurrent.duration.FiniteDuration

trait Transport[F[_]] {
  def get(uri: String): F[Either[Transport.Error, Response]]
}

object Transport {
  case class Response(data: String, expiresIn: Option[FiniteDuration])

  sealed trait Error extends ProductSerializableNoStackTrace

  object Error {
    case class InvalidUrl(providedUrl: String) extends Error
    case class UnexpectedResponse(statusCode: Int, response: Option[String]) extends Error
    case class UnexpectedError(cause: Throwable) extends Error
  }

}
