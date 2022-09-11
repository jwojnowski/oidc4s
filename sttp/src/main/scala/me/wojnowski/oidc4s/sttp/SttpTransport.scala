package me.wojnowski.oidc4s.sttp

import cats.Applicative
import cats.Monad
import me.wojnowski.oidc4s.HttpTransport
import sttp.client3._

import scala.util.Try
import cats.syntax.all._
import me.wojnowski.oidc4s
import me.wojnowski.oidc4s.HttpTransport.Error
import sttp.model.Header
import sttp.model.HeaderNames.CacheControl
import sttp.model.Headers
import sttp.model.headers.CacheDirective
import sttp.monad.MonadError
import sttp.monad.syntax._

import scala.concurrent.duration.Duration
import scala.util.control.NonFatal

object SttpTransport {

  def instance[F[_]](backend: SttpBackend[F, Any]): HttpTransport[F] = new HttpTransport[F] {
    private implicit val monadError: MonadError[F] = backend.responseMonad

    override def get(url: String): F[Either[HttpTransport.Error, HttpTransport.Response]] =
      Try(uri"$url").toEither match {
        case Right(uri) =>
          backend
            .send(basicRequest.get(uri).response(asString))
            .map(response =>
              response
                .body
                .map { data =>
                  val maybeExpiresIn =
                    response
                      .header(CacheControl)
                      .flatMap(value =>
                        CacheDirective.parse(value).collectFirst { case Right(CacheDirective.MaxAge(finiteDuration)) => finiteDuration }
                      )
                  HttpTransport.Response(data, expiresIn = maybeExpiresIn) // TODO Age?
                }
                .leftMap { errorResponse =>
                  Error.UnexpectedResponse(response.code.code, errorResponse.some)
                }
                .leftWiden[Error]
            )
            .handleError { case NonFatal(throwable) =>
              Error.UnexpectedError(throwable).asLeft[HttpTransport.Response].leftWiden[Error].unit
            }

        case Left(_) =>
          Error.InvalidUrl(url).asLeft[HttpTransport.Response].leftWiden[Error].unit

      }

  }

}
