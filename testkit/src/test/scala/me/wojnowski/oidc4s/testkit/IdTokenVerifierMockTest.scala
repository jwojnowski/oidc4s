package me.wojnowski.oidc4s.testkit

import cats.Applicative
import cats.Id
import io.circe.Decoder
import me.wojnowski.oidc4s.json.JsonSupport
import me.wojnowski.oidc4s.json.circe.CirceJsonSupport
import me.wojnowski.oidc4s.ClientId
import me.wojnowski.oidc4s.IdTokenClaims
import me.wojnowski.oidc4s.IdTokenVerifier
import me.wojnowski.oidc4s.Issuer
import munit.FunSuite

import java.time.Instant
import CirceJsonSupport._
import cats.data.NonEmptySet
import cats.data.NonEmptySetImpl
import cats.effect.Clock
import me.wojnowski.oidc4s.IdTokenClaims.Audience
import me.wojnowski.oidc4s.IdTokenClaims.Subject
import me.wojnowski.oidc4s.testkit.IdTokenVerifierMockTest._

import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration

class IdTokenVerifierMockTest extends FunSuite {
  implicit val circeJsonSupport: JsonSupport = CirceJsonSupport

  val rawClaimsMock: IdTokenVerifier[Id] = IdTokenVerifierMock.constRawClaimsEitherPF {
    case `token1`       => Right(rawClaims1)
    case `token2`       => Right(rawClaims2)
    case `expiredToken` => Left(IdTokenVerifier.Error.TokenExpired(Instant.EPOCH.plusSeconds(30)))
  }

  val standardClaimsMock: IdTokenVerifier[Id] = IdTokenVerifierMock.constStandardClaimsEitherPF {
    case `token1`       => Right(standardClaims1)
    case `expiredToken` => Left(IdTokenVerifier.Error.TokenExpired(Instant.EPOCH.plusSeconds(2237)))
  }

  val subjectClaimsMock: IdTokenVerifier[Id] = IdTokenVerifierMock.constSubject(subject1)

  test("Raw claims mock differentiates between tokens") {
    val result1 = rawClaimsMock.verify(token1, ClientId("client-id-1"))
    val result2 = rawClaimsMock.verify(token2, ClientId("client-id-2"))

    val expected1 = Right(IdTokenClaims.Subject("user-id-1"))
    val expected2 = Right(IdTokenClaims.Subject("user-id-2"))

    assertEquals(result1, expected1)
    assertEquals(result2, expected2)
  }

  test("Raw claims checks client ID") {
    val result = rawClaimsMock.verifyAndDecodeCustom[CustomClaims](token1, ClientId("invalid-client-id"))
    val expected = Left(IdTokenVerifier.Error.ClientIdDoesNotMatch)

    assertEquals(result, expected)
  }

  test("Raw claims mock decodes custom token") {
    val result = rawClaimsMock.verifyAndDecodeCustom[CustomClaims](token1)

    val expected = Right(CustomClaims("foo"))

    assertEquals(result, expected)
  }

  test("Raw claims mock returns a failure for a matching, invalid token") {
    val result = rawClaimsMock.verifyAndDecodeCustom[CustomClaims](expiredToken)
    val expected = Left(IdTokenVerifier.Error.TokenExpired(Instant.EPOCH.plusSeconds(30)))

    assertEquals(result, expected)
  }

  test("Raw claims mock returns a failure for an unknown token") {
    val result = rawClaimsMock.verifyAndDecodeCustom[CustomClaims]("unknown.token")
    val expected = Left(IdTokenVerifier.Error.MalformedToken)

    assertEquals(result, expected)
  }

  test("Standard claims checks client ID") {
    val result = standardClaimsMock.verify(token1, ClientId("invalid-client-id"))
    val expected = Left(IdTokenVerifier.Error.ClientIdDoesNotMatch)

    assertEquals(result, expected)
  }

  test("Subject claims") {
    val result = subjectClaimsMock.verify(token1, ClientId("https://example.com"))
    val expected = Right(subject1)

    assertEquals(result, expected)
  }

  test("Subject claims checks client ID") {
    val result = subjectClaimsMock.verify(token1, ClientId("invalid-client-id"))
    val expected = Left(IdTokenVerifier.Error.ClientIdDoesNotMatch)

    assertEquals(result, expected)
  }

  test("Subject claims generates full IdTokenClaims") {
    val result = subjectClaimsMock.verifyAndDecode(token1)
    val expected = Right(
      IdTokenClaims(
        subject = subject1,
        audience = NonEmptySet.of(Audience("https://example.com")),
        issuer = Issuer("https://example.com"),
        issuedAt = clock.realTimeInstant,
        expiration = clock.realTimeInstant.plusSeconds(600)
      )
    )

    assertEquals(result, expected)
  }
}

object IdTokenVerifierMockTest {
  val rawClaims1 =
    """
      {
        "sub": "user-id-1",
        "aud": "client-id-1",
        "customClaim": "foo",
        "iss": "https://id.example.com",
        "iat": 2137,
        "exp": 2237
      }
    """

  val rawClaims2 =
    """
      {
        "sub": "user-id-2",
        "aud": "client-id-2",
        "iss": "https://id.example.com",
        "iat": 2137,
        "exp": 2237
      }
    """

  val token1 = "valid.token.1"
  val token2 = "valid.token.2"
  val expiredToken = "expired.token.3"

  val subject1 = Subject("subject-1")

  val standardClaims1 = IdTokenClaims(
    subject = subject1,
    audience = NonEmptySet.of(Audience("client-id-1")),
    issuer = Issuer("https://id.example.com"),
    issuedAt = Instant.EPOCH.plusSeconds(2137),
    expiration = Instant.EPOCH.plusSeconds(2237)
  )

  case class CustomClaims(customClaim: String)

  object CustomClaims {
    implicit val decoder: Decoder[CustomClaims] = Decoder.forProduct1("customClaim")(CustomClaims.apply)
  }

  implicit val clock: Clock[Id] = new Clock[Id] {
    override def applicative: Applicative[Id] = Applicative[Id]

    override def monotonic: Id[FiniteDuration] = Duration.Zero

    override def realTime: Id[FiniteDuration] = Duration.Zero
  }

}
