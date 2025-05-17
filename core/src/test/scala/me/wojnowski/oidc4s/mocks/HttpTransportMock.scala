package me.wojnowski.oidc4s.mocks

import me.wojnowski.oidc4s.transport.Transport
import me.wojnowski.oidc4s.transport.Transport.Response

import cats.Applicative
import cats.syntax.all._

object HttpTransportMock {

  def const[F[_]: Applicative](
    expectedUrl: String,
    response: String,
    unexpectedResponse: String => Transport.Error = (uri: String) =>
      Transport.Error.UnexpectedResponse(404, s"The given URL [$uri] is unexpected... 🤔".some)
  ): Transport[F] =
    (uri: String) =>
      Either
        .cond(
          uri === expectedUrl,
          Response(response, expiresIn = None),
          unexpectedResponse(uri)
        )
        .pure[F]

}
