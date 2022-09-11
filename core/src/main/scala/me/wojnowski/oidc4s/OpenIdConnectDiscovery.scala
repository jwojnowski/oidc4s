package me.wojnowski.oidc4s

import cats.Monad
import cats.data.EitherT
import cats.syntax.all._
import me.wojnowski.oidc4s.OpenIdConnectDiscovery.Error.CouldNotDecodeResponse
import me.wojnowski.oidc4s.OpenIdConnectDiscovery.Error.CouldNotFetchResponse
import me.wojnowski.oidc4s.cache.Cache

trait OpenIdConnectDiscovery[F[_]] {
  def getConfig: F[Either[OpenIdConnectDiscovery.Error, OpenIdConfig]]
}

object OpenIdConnectDiscovery {

  def instance[F[_]: Monad](
    location: Location
  )(
    httpTransport: HttpTransport[F],
    jsonSupport: JsonSupport,
    cache: Cache[F, OpenIdConfig]
  ): OpenIdConnectDiscovery[F] =
    new OpenIdConnectDiscovery[F] {

      import jsonSupport._

      override def getConfig: F[Either[OpenIdConnectDiscovery.Error, OpenIdConfig]] =
        cache
          .get
          .flatMap {
            case Some(config) =>
              config.asRight[Error].pure[F]
            case None         =>
              {
                for {
                  rawResponse <- EitherT(httpTransport.get(s"${location.url}/.well-known/openid-configuration"))
                                   .leftMap(CouldNotFetchResponse.apply)
                                   .leftWiden[Error]
                  config      <- EitherT
                                   .fromEither(JsonDecoder[OpenIdConfig].decode(rawResponse.data))
                                   .leftMap(details => CouldNotDecodeResponse(details))
                                   .leftWiden[Error]
                  _           <- EitherT.liftF[F, Error, Unit](cache.put(config, rawResponse.expiresIn))
                } yield config
              }.value
          }

    }

  def static[F[_]: Monad](config: OpenIdConfig): OpenIdConnectDiscovery[F] = new OpenIdConnectDiscovery[F] {
    override def getConfig: F[Either[Error, OpenIdConfig]] = config.asRight[Error].pure[F]
  }

  sealed trait Error extends ProductSerializableNoStacktrace

  object Error {
    case class CouldNotDecodeResponse(details: String) extends Error
    case class CouldNotFetchResponse(cause: HttpTransport.Error) extends Error
  }

}
