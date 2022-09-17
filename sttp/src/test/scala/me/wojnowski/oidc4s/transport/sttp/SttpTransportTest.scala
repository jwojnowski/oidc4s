package me.wojnowski.oidc4s.transport.sttp

import me.wojnowski.oidc4s.transport.Transport
import me.wojnowski.oidc4s.transport.Transport.Error.InvalidUrl
import me.wojnowski.oidc4s.transport.Transport.Error.UnexpectedError
import me.wojnowski.oidc4s.transport.Transport.Error.UnexpectedResponse
import munit.FunSuite
import sttp.client3.Response
import sttp.client3.UriContext
import sttp.client3.testing.SttpBackendStub
import sttp.model.Header
import sttp.model.HeaderNames
import sttp.model.Method
import sttp.model.StatusCode
import sttp.monad.TryMonad

import scala.concurrent.duration.FiniteDuration
import scala.util.Success
import scala.util.Try

class SttpTransportTest extends FunSuite {

  val correctUrl = "http://appleid.apple.com/.well-known/openid-configuration"

  test("Invalid URL") {
    val backend: SttpBackendStub[Try, Nothing] = SttpBackendStub(TryMonad)

    val transport = SttpTransport.instance[Try](backend)

    val invalidUrl = ""

    val result = transport.get(invalidUrl)

    assertEquals(result, expected = Success(Left(InvalidUrl(invalidUrl))))
  }

  test("Unexpected response") {
    val backend: SttpBackendStub[Try, Nothing] =
      SttpBackendStub(TryMonad)
        .whenAnyRequest
        .thenRespondWithCode(StatusCode.InternalServerError, "oops")

    val transport = SttpTransport.instance[Try](backend)

    val result = transport.get(correctUrl)

    assertEquals(result, expected = Success(Left(UnexpectedResponse(500, Some("oops")))))
  }

  test("Connection error") {
    val backend: SttpBackendStub[Try, Nothing] =
      SttpBackendStub(TryMonad)

    val transport = SttpTransport.instance[Try](backend)

    val result = transport.get(correctUrl)

    assert(result.get.left.forall(_.isInstanceOf[UnexpectedError]))
  }

  test("Correct response (no cache headers)") {
    val backend: SttpBackendStub[Try, Nothing] =
      SttpBackendStub(TryMonad)
        .whenRequestMatches(request => request.method == Method.GET && request.uri == uri"$correctUrl")
        .thenRespondWithCode(StatusCode.Ok, "data")

    val transport = SttpTransport.instance[Try](backend)

    val result = transport.get(correctUrl)

    assertEquals(result, expected = Success(Right(Transport.Response("data", expiresIn = None))))
  }

  test("Correct response (invalid cache headers)") {
    val backend: SttpBackendStub[Try, Nothing] =
      SttpBackendStub(TryMonad)
        .whenRequestMatches(request => request.method == Method.GET && request.uri == uri"$correctUrl")
        .thenRespond(Response("data", StatusCode.Ok, "OK", Seq(Header(HeaderNames.CacheControl, "this-makes-no-sense"))))

    val transport = SttpTransport.instance[Try](backend)

    val result = transport.get(correctUrl)

    assertEquals(result, expected = Success(Right(Transport.Response("data", expiresIn = None))))
  }

  test("Correct response (valid cache headers, no age)") {
    val backend: SttpBackendStub[Try, Nothing] =
      SttpBackendStub(TryMonad)
        .whenRequestMatches(request => request.method == Method.GET && request.uri == uri"$correctUrl")
        .thenRespond(
          Response(
            "data",
            StatusCode.Ok,
            "OK",
            Seq(Header(HeaderNames.CacheControl, "no-transform,max-age=86400,stale-while-revalidate"))
          )
        )

    val transport = SttpTransport.instance[Try](backend)

    val result = transport.get(correctUrl)

    assertEquals(result, expected = Success(Right(Transport.Response("data", expiresIn = Some(FiniteDuration(1, "day"))))))
  }

  test("Correct response (valid cache headers, age present)") {
    val backend: SttpBackendStub[Try, Nothing] =
      SttpBackendStub(TryMonad)
        .whenRequestMatches(request => request.method == Method.GET && request.uri == uri"$correctUrl")
        .thenRespond(
          Response(
            "data",
            StatusCode.Ok,
            "OK",
            Seq(
              Header(HeaderNames.CacheControl, "no-transform,max-age=86400,stale-while-revalidate"),
              Header(HeaderNames.Age, "3600")
            )
          )
        )

    val transport = SttpTransport.instance[Try](backend)

    val result = transport.get(correctUrl)

    assertEquals(result, expected = Success(Right(Transport.Response("data", expiresIn = Some(FiniteDuration(23, "hours"))))))
  }

}
