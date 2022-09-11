package me.wojnowski.oidc4s

import cats.Applicative
import cats.syntax.all._
import me.wojnowski.oidc4s.HttpTransport.Response

object HttpTransportMock {

  def const[F[_]: Applicative](
    expectedUrl: String,
    response: String,
    unexpectedResponse: String => HttpTransport.Error = (uri: String) =>
      HttpTransport.Error.UnexpectedResponse(404, s"The given URL [$uri] is unexpected... 🤔".some)
  ): HttpTransport[F] =
    (uri: String) =>
      Either
        .cond(
          uri === expectedUrl,
          Response(response, expiresIn = None),
          unexpectedResponse(uri)
        )
        .pure[F]

}
