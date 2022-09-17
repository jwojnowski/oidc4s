package me.wojnowski.oidc4s

import me.wojnowski.oidc4s.HttpTransport.Response

import java.time.Instant
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration

trait HttpTransport[F[_]] {
  def get(uri: String): F[Either[HttpTransport.Error, Response]]
}

object HttpTransport {
  case class Response(data: String, expiresIn: Option[FiniteDuration])

  sealed trait Error extends ProductSerializableNoStackTrace

  object Error {
    case class InvalidUrl(providedUrl: String) extends Error
    case class UnexpectedResponse(statusCode: Int, response: Option[String]) extends Error
    case class UnexpectedError(cause: Throwable) extends Error
  }
}
