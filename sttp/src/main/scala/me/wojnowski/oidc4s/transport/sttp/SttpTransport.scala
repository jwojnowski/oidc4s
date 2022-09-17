package me.wojnowski.oidc4s.transport.sttp

import cats.syntax.all._
import me.wojnowski.oidc4s.transport.Transport
import me.wojnowski.oidc4s.transport.Transport.Error
import sttp.client3._
import sttp.model.HeaderNames
import sttp.model.headers.CacheDirective
import sttp.monad.MonadError
import sttp.monad.syntax._

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration
import scala.util.Try
import scala.util.control.NonFatal

object SttpTransport {

  def instance[F[_]](backend: SttpBackend[F, Any]): Transport[F] = new Transport[F] {
    private implicit val monadError: MonadError[F] = backend.responseMonad

    override def get(url: String): F[Either[Transport.Error, Transport.Response]] =
      Try(uri"$url").toEither match {
        case Right(uri) =>
          backend
            .send(basicRequest.get(uri).response(asString))
            .map(response =>
              response
                .body
                .map { data =>
                  val maybeMaxAge =
                    response
                      .header(HeaderNames.CacheControl)
                      .flatMap { value =>
                        CacheDirective.parse(value).collectFirst { case Right(CacheDirective.MaxAge(finiteDuration)) => finiteDuration }
                      }

                  val maybeAge =
                    response
                      .header(HeaderNames.Age)
                      .flatMap { value =>
                        value.toLongOption.filter(_ >= 0)
                      }
                      .map { seconds =>
                        FiniteDuration(seconds, TimeUnit.SECONDS)
                      }

                  val maybeExpiresIn =
                    maybeMaxAge.map { maxAge =>
                      maxAge - maybeAge.getOrElse(Duration.Zero)
                    }

                  Transport.Response(data, expiresIn = maybeExpiresIn)
                }
                .leftMap { errorResponse =>
                  Error.UnexpectedResponse(response.code.code, errorResponse.some)
                }
                .leftWiden[Error]
            )
            .handleError { case NonFatal(throwable) =>
              Error.UnexpectedError(throwable).asLeft[Transport.Response].leftWiden[Error].unit
            }

        case Left(_) =>
          Error.InvalidUrl(url).asLeft[Transport.Response].leftWiden[Error].unit

      }

  }

}
