package me.wojnowski.oidc4s.transport.sttp4

import me.wojnowski.oidc4s.transport.Transport
import me.wojnowski.oidc4s.transport.Transport.Error.InvalidUrl
import me.wojnowski.oidc4s.transport.Transport.Error.UnexpectedError
import me.wojnowski.oidc4s.transport.Transport.Error.UnexpectedResponse
import me.wojnowski.oidc4s.transport.sttp4.SttpTransport

import scala.concurrent.duration.FiniteDuration
import scala.util.Success

import munit.FunSuite
import sttp.client4.UriContext
import sttp.client4.testing.ResponseStub
import sttp.client4.testing.SyncBackendStub
import sttp.client4.wrappers.TryBackend
import sttp.model.Header
import sttp.model.HeaderNames
import sttp.model.Method
import sttp.model.StatusCode

class SttpTransportTest extends FunSuite {

  val correctUrl = "http://appleid.apple.com/.well-known/openid-configuration"

  test("Invalid URL") {
    val transport = SttpTransport.instance(TryBackend(SyncBackendStub))

    val invalidUrl = ""

    val result = transport.get(invalidUrl)

    assertEquals(result, expected = Success(Left(InvalidUrl(invalidUrl))))
  }

  test("Unexpected response") {
    val transport = SttpTransport.instance(
      TryBackend(
        SyncBackendStub
          .whenAnyRequest
          .thenRespond(ResponseStub.adjust("oops", StatusCode.InternalServerError))
      )
    )

    val result = transport.get(correctUrl)

    assertEquals(result, expected = Success(Left(UnexpectedResponse(500, Some("oops")))))
  }

  test("Connection error") {
    val transport = SttpTransport.instance(TryBackend(SyncBackendStub))

    val result = transport.get(correctUrl)

    assert(result.get.left.exists(_.isInstanceOf[UnexpectedError]))
  }

  test("Correct response (no cache headers)") {
    val transport = SttpTransport.instance(
      TryBackend(
        SyncBackendStub
          .whenRequestMatches(request => request.method == Method.GET && request.uri == uri"$correctUrl")
          .thenRespond(ResponseStub.adjust("data", StatusCode.Ok))
      )
    )

    val result = transport.get(correctUrl)

    assertEquals(result, expected = Success(Right(Transport.Response("data", expiresIn = None))))
  }

  test("Correct response (invalid cache headers)") {
    val transport = SttpTransport.instance(
      TryBackend(
        SyncBackendStub
          .whenRequestMatches(request => request.method == Method.GET && request.uri == uri"$correctUrl")
          .thenRespond(
            ResponseStub.adjust("data", StatusCode.Ok, Seq(Header(HeaderNames.CacheControl, "this-makes-no-sense")))
          )
      )
    )

    val result = transport.get(correctUrl)

    assertEquals(result, expected = Success(Right(Transport.Response("data", expiresIn = None))))
  }

  test("Correct response (valid cache headers, no age)") {
    val transport = SttpTransport.instance(
      TryBackend(
        SyncBackendStub
          .whenRequestMatches(request => request.method == Method.GET && request.uri == uri"$correctUrl")
          .thenRespond(
            ResponseStub.adjust(
              "data",
              StatusCode.Ok,
              Seq(Header(HeaderNames.CacheControl, "no-transform,max-age=86400,stale-while-revalidate"))
            )
          )
      )
    )

    val result = transport.get(correctUrl)

    assertEquals(result, expected = Success(Right(Transport.Response("data", expiresIn = Some(FiniteDuration(1, "day"))))))
  }

  test("Correct response (valid cache headers, age present)") {
    val transport = SttpTransport.instance(
      TryBackend(
        SyncBackendStub
          .whenRequestMatches(request => request.method == Method.GET && request.uri == uri"$correctUrl")
          .thenRespond(
            ResponseStub.adjust(
              "data",
              StatusCode.Ok,
              Seq(
                Header(HeaderNames.CacheControl, "no-transform,max-age=86400,stale-while-revalidate"),
                Header(HeaderNames.Age, "3600")
              )
            )
          )
      )
    )

    val result = transport.get(correctUrl)

    assertEquals(result, expected = Success(Right(Transport.Response("data", expiresIn = Some(FiniteDuration(23, "hours"))))))
  }

}
