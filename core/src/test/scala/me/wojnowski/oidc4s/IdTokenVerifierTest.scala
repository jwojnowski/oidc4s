package me.wojnowski.oidc4s

import cats.Applicative
import cats.data.NonEmptySet
import cats.data.NonEmptyVector
import cats.effect.IO
import cats.effect.testkit.TestControl
import cats.syntax.all._
import me.wojnowski.oidc4s.IdTokenClaims._
import me.wojnowski.oidc4s.IdTokenVerifier.Error.JwtVerificationError
import me.wojnowski.oidc4s.IdTokenVerifier.Error.UnexpectedIssuer
import me.wojnowski.oidc4s.IdTokenVerifierTest.staticKeyProvider
import me.wojnowski.oidc4s.PublicKeyProvider.KeyId
import me.wojnowski.oidc4s.PublicKeyProvider.KeyMap
import me.wojnowski.oidc4s.TimeUtils.InstantToFiniteDuration
import me.wojnowski.oidc4s.config.Location
import me.wojnowski.oidc4s.config.OpenIdConfig
import me.wojnowski.oidc4s.config.OpenIdConnectDiscovery
import me.wojnowski.oidc4s.json.JsonDecoder
import me.wojnowski.oidc4s.mocks.CacheMock
import me.wojnowski.oidc4s.mocks.HttpTransportMock
import me.wojnowski.oidc4s.mocks.JsonSupportMock
import munit.CatsEffectSuite
import pdi.jwt.JwtHeader
import pdi.jwt.exceptions.JwtEmptyAlgorithmException
import pdi.jwt.exceptions.JwtEmptySignatureException
import pdi.jwt.exceptions.JwtExpirationException
import pdi.jwt.exceptions.JwtValidationException

import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64
import scala.annotation.unused

//noinspection ZeroIndexToHead
class IdTokenVerifierTest extends CatsEffectSuite {

  import IdTokenVerifierTest.TestValues._

  private val jsonSupport =
    JsonSupportMock.instance(
      idTokenTranslations = rawIdTokenClaims.zip(decodedIdTokenClaims).toMap,
      jwtHeaderTranslations = rawJwtHeaders.zip(decodedJwtHeaders).toMap
    )

  private val discovery = OpenIdConnectDiscovery.static[IO](OpenIdConfig(issuer = Issuer("https://accounts.google.com"), jwksUri = ""))

  test("Correct IdentityToken signed with RS256") {
    runAtInstant(idTokenExpiration.minusSeconds(3)) {
      IdTokenVerifier
        .create[IO](staticKeyProvider(googleKeys), discovery, jsonSupport)
        .verifyAndDecode(rawIdToken)
        .map { result =>
          assertEquals(result, Right(decodedIdTokenClaims(0)))
        }
    }
  }

  test("Expired IdentityToken") {
    runAtInstant(idTokenExpiration.plusSeconds(3)) {
      IdTokenVerifier
        .create[IO](staticKeyProvider(googleKeys), discovery, jsonSupport)
        .verifyAndDecode(rawIdToken)
        .map {
          case Left(JwtVerificationError(_: JwtExpirationException)) => ()
          case _                                                     => fail("expected JwtExpirationException")
        }
    }
  }

  test("Custom claim decoding (correct issuer)") {
    runAtInstant(idTokenExpiration.minusSeconds(3)) {
      case class CustomClaims(email: String, email_verified: Boolean)

      val expectedCustomClaims = CustomClaims(email = "integration-tests@chingor-test.iam.gserviceaccount.com", email_verified = true)

      implicit val jsonDecoder: JsonDecoder[(CustomClaims, IdTokenClaims)] = (rawClaims: String) =>
        if (rawClaims === rawIdTokenClaims.head) {
          Right(expectedCustomClaims, decodedIdTokenClaims.head)
        } else {
          Left("Could not decode claims")
        }

      IdTokenVerifier
        .create[IO](staticKeyProvider(googleKeys), discovery, jsonSupport)
        .verifyAndDecodeCustom[CustomClaims](rawIdToken)
        .map { result =>
          assertEquals(
            result,
            Right(expectedCustomClaims)
          )
        }
    }
  }

  test("Custom claim decoding (incorrect issuer)") {
    runAtInstant(idTokenExpiration.minusSeconds(3)) {
      case class CustomClaims(email: String, email_verified: Boolean)

      val expectedCustomClaims = CustomClaims(email = "integration-tests@chingor-test.iam.gserviceaccount.com", email_verified = true)
      val unexpectedIssuer = Issuer("https://spanish-inquisition.example.com")

      implicit val jsonDecoder: JsonDecoder[(CustomClaims, IdTokenClaims)] = (rawClaims: String) =>
        if (rawClaims === rawIdTokenClaims.head) {
          Right(expectedCustomClaims, decodedIdTokenClaims.head.copy(issuer = unexpectedIssuer))
        } else {
          Left("Could not decode claims")
        }

      IdTokenVerifier
        .create[IO](staticKeyProvider(googleKeys), discovery, jsonSupport)
        .verifyAndDecodeCustom[CustomClaims](rawIdToken)
        .map { result =>
          assertEquals(
            result,
            Left(UnexpectedIssuer(found = unexpectedIssuer, decodedIdTokenClaims.head.issuer))
          )
        }
    }
  }

  test("Matching Client ID") {
    runAtInstant(idTokenExpiration.minusSeconds(3)) {
      IdTokenVerifier
        .create[IO](staticKeyProvider(googleKeys), discovery, jsonSupport)
        .verify(rawIdToken, clientId)
        .map { result =>
          assertEquals(
            result,
            Right(decodedIdTokenClaims.apply(0).subject)
          )
        }
    }
  }

  test("Not matching Client ID") {
    runAtInstant(idTokenExpiration.minusSeconds(3)) {
      val otherClientId = ClientId("other-client-id")

      IdTokenVerifier
        .create[IO](staticKeyProvider(googleKeys), discovery, jsonSupport)
        .verify(rawIdToken, otherClientId)
        .map { result =>
          assertEquals(
            result,
            Left(IdTokenVerifier.Error.ClientIdDoesNotMatch)
          )
        }
    }
  }

  test("Incorrect issuer") {
    val tokenWithOtherIssuer =
      "eyJhbGciOiJSUzI1NiIsImtpZCI6IjExZTAzZjM5YjhkMzAwYzhjOWExYjgwMGRkZWJmY2ZkZTQxNTJjMGMiLCJ0eXAiOiJKV1QifQ.eyJhdWQiOiJodHRwczovL2V4YW1wbGUuY29tL3BhdGgiLCJhenAiOiJpbnRlZ3JhdGlvbi10ZXN0c0BjaGluZ29yLXRlc3QuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLCJlbWFpbCI6ImludGVncmF0aW9uLXRlc3RzQGNoaW5nb3ItdGVzdC5pYW0uZ3NlcnZpY2VhY2NvdW50LmNvbSIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJleHAiOjE1ODc2Mjk4ODgsImlhdCI6MTU4NzYyNjI4OCwiaXNzIjoiaHR0cHM6Ly90aGlzaXNub3Rnb29nbGUuY29tIiwic3ViIjoiMTA0MDI5MjkyODUzMDk5OTc4MjkzIn0.g_FcdLByRX9STZaIWj9OGPZu-t6OtJUtw3h-7KjxHeVoGD7DBvy0qcRrS66ieVDowRcuvAWgBvdd6FNH8uHUiFYzx2But5abVQgZEZfUQLamWXnPH_Y-HdieC7vq3HxeKtBpBAFKt_LNSsw3xoA5sT_CJPsK-JG-GeO6BpY3cEbaNt0p7bRpe3YdaL7m86p045r9WwkXRcHbzF2OEHM2GR1zguYIXEax_VUPFTEV0V2xE04yxiQ0TbcXsxz3jC_SDoJVK0uchtGHl8vtxaS3JHEJuUr10OquwoR3-uDamMjlQyxdz3EIBqq9Z6zUXY9cJKIvaWSNO4BeNzZBxc8vEA"

    runAtInstant(idTokenExpiration.minusSeconds(3)) {
      IdTokenVerifier
        .create[IO](staticKeyProvider(nonGoogleKeys), discovery, jsonSupport)
        .verifyAndDecode(tokenWithOtherIssuer)
        .map {
          case Left(IdTokenVerifier.Error.UnexpectedIssuer(Issuer("https://thisisnotgoogle.com"), issuer)) =>
            assertEquals(issuer, Issuer("https://accounts.google.com"))
          case e                                                                                           =>
            fail(s"expected UnexpectedIssuer, got $e")
        }
    }
  }

  test("Public key mismatch") {
    val tokenSignedWithOtherKey =
      "eyJhbGciOiJSUzI1NiIsImtpZCI6ImY5ZDk3YjRjYWU5MGJjZDc2YWViMjAwMjZmNmI3NzBjYWMyMjE3ODMiLCJ0eXAiOiJKV1QifQ.eyJhdWQiOiJodHRwczovL2V4YW1wbGUuY29tL3BhdGgiLCJhenAiOiJpbnRlZ3JhdGlvbi10ZXN0c0BjaGluZ29yLXRlc3QuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLCJlbWFpbCI6ImludGVncmF0aW9uLXRlc3RzQGNoaW5nb3ItdGVzdC5pYW0uZ3NlcnZpY2VhY2NvdW50LmNvbSIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJleHAiOjE1ODc2Mjk4ODgsImlhdCI6MTU4NzYyNjI4OCwiaXNzIjoiaHR0cHM6Ly9hY2NvdW50cy5nb29nbGUuY29tIiwic3ViIjoiMTA0MDI5MjkyODUzMDk5OTc4MjkzIn0.o9i5PALsROkAspFWCqzbarbVasth0jUOaTTsDYQG9pm3clGcjXRH4wPpHC7K0igBfx0rfkscxAtmeBzUmwLjkeyvOc2OX4hNo7LkE8EHPM8AqASj2CUOWg0fHfV6coXLa0FFcp9khzQxc0dFN4pyvZo-7ANVl7jRMDYcSMK0WCjM00jnDygq-7zKmAOWmdonVnCSQ6bUyuSNMDsUSuuWxaaYjTvscg1gnXPq5kaInadbBN-EHN7OECQD3bXIpy0CTF-qyGiajevKCR8tIFI86Y9-jEQCMwTgZEm6-VURaPVHkIqyeoFUytaNwq13f2bE_H-csRQ7an3t6_VZeXly7g"

    runAtInstant(idTokenExpiration.minusSeconds(3)) {
      IdTokenVerifier
        .create[IO](staticKeyProvider(googleKeys), discovery, jsonSupport)
        .verifyAndDecode(tokenSignedWithOtherKey)
        .map {
          case Left(JwtVerificationError(_: JwtValidationException)) => ()
          case e                                                     => fail(s"expected JwtValidationException, got $e")
        }
    }
  }

  test("Overridden issuer") {
    val tokenWithOtherIssuer =
      "eyJhbGciOiJSUzI1NiIsImtpZCI6IjExZTAzZjM5YjhkMzAwYzhjOWExYjgwMGRkZWJmY2ZkZTQxNTJjMGMiLCJ0eXAiOiJKV1QifQ.eyJhdWQiOiJodHRwczovL2V4YW1wbGUuY29tL3BhdGgiLCJhenAiOiJpbnRlZ3JhdGlvbi10ZXN0c0BjaGluZ29yLXRlc3QuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLCJlbWFpbCI6ImludGVncmF0aW9uLXRlc3RzQGNoaW5nb3ItdGVzdC5pYW0uZ3NlcnZpY2VhY2NvdW50LmNvbSIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJleHAiOjE1ODc2Mjk4ODgsImlhdCI6MTU4NzYyNjI4OCwiaXNzIjoiaHR0cHM6Ly90aGlzaXNub3Rnb29nbGUuY29tIiwic3ViIjoiMTA0MDI5MjkyODUzMDk5OTc4MjkzIn0.g_FcdLByRX9STZaIWj9OGPZu-t6OtJUtw3h-7KjxHeVoGD7DBvy0qcRrS66ieVDowRcuvAWgBvdd6FNH8uHUiFYzx2But5abVQgZEZfUQLamWXnPH_Y-HdieC7vq3HxeKtBpBAFKt_LNSsw3xoA5sT_CJPsK-JG-GeO6BpY3cEbaNt0p7bRpe3YdaL7m86p045r9WwkXRcHbzF2OEHM2GR1zguYIXEax_VUPFTEV0V2xE04yxiQ0TbcXsxz3jC_SDoJVK0uchtGHl8vtxaS3JHEJuUr10OquwoR3-uDamMjlQyxdz3EIBqq9Z6zUXY9cJKIvaWSNO4BeNzZBxc8vEA"

    val discoveryWithDifferentIssuer =
      OpenIdConnectDiscovery.static[IO](config.OpenIdConfig(issuer = Issuer("https://thisisnotgoogle.com"), jwksUri = ""))

    runAtInstant(idTokenExpiration.minusSeconds(3)) {
      IdTokenVerifier
        .create[IO](staticKeyProvider(nonGoogleKeys), discoveryWithDifferentIssuer, jsonSupport)
        .verifyAndDecode(tokenWithOtherIssuer)
        .map { result =>
          assertEquals(result, Right(decodedIdTokenClaims.apply(1)))
        }
    }

  }

  test("Non-existent issuer") {
    val tokenWithoutIssuer =
      "eyJhbGciOiJSUzI1NiIsImtpZCI6IjExZTAzZjM5YjhkMzAwYzhjOWExYjgwMGRkZWJmY2ZkZTQxNTJjMGMiLCJ0eXAiOiJKV1QifQ.eyJhdWQiOiJodHRwczovL2V4YW1wbGUuY29tL3BhdGgiLCJhenAiOiJpbnRlZ3JhdGlvbi10ZXN0c0BjaGluZ29yLXRlc3QuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLCJlbWFpbCI6ImludGVncmF0aW9uLXRlc3RzQGNoaW5nb3ItdGVzdC5pYW0uZ3NlcnZpY2VhY2NvdW50LmNvbSIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJleHAiOjE1ODc2Mjk4ODgsImlhdCI6MTU4NzYyNjI4OCwic3ViIjoiMTA0MDI5MjkyODUzMDk5OTc4MjkzIn0.XlXLftspYjhMbEVnHca3LiB2KI-jYufK_7KdX0_l9OScKNlAvX_xOGrW_eh5lfEcrofotrqAy7IEa5d9_rcVvZALQfWMf7xX7rUv5q1EGX11wdq6Av-etZqSRhzYqtPjVRJ-mvqV4mUzcFPNlvf6mUm_sMZlBZrVoaOx8I3XuoYmG8gf-LLOgDt6_gtaWK8XWoUmXR_Ngph8r1Uv0PcoBRHuuX8eyJFfQryFRtEb7pLTlu1Ri_zeUakQ_A_BYKQwABbZZWmpzv43NpOX4PB-4c9v9EBM1y3O6K6Z97-J-II0ToebKG4l-aOTMZ6p7-5D15Oxa3g7Ku9qB3Q8SfzpLg"

    runAtInstant(idTokenExpiration.minusSeconds(3)) {
      IdTokenVerifier
        .create[IO](staticKeyProvider(nonGoogleKeys), discovery, jsonSupport)
        .verifyAndDecode(tokenWithoutIssuer)
        .map {
          case Left(IdTokenVerifier.Error.CouldNotDecodeClaim(_)) => ()
          case e                                                  => fail(s"expected JwtEmptySignatureException, got $e")
        }
    }
  }

  test("Algorithm: none and empty signature") {
    val tokenWithAlgorithmNone =
      "eyJhbGciOiJub25lIiwia2lkIjoiMTFlMDNmMzliOGQzMDBjOGM5YTFiODAwZGRlYmZjZmRlNDE1MmMwYyIsInR5cCI6IkpXVCJ9Cg.eyJhdWQiOiJodHRwczovL2V4YW1wbGUuY29tL3BhdGgiLCJhenAiOiJpbnRlZ3JhdGlvbi10ZXN0c0BjaGluZ29yLXRlc3QuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLCJlbWFpbCI6ImludGVncmF0aW9uLXRlc3RzQGNoaW5nb3ItdGVzdC5pYW0uZ3NlcnZpY2VhY2NvdW50LmNvbSIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJleHAiOjE1ODc2Mjk4ODgsImlhdCI6MTU4NzYyNjI4OCwiaXNzIjoiaHR0cHM6Ly9hY2NvdW50cy5nb29nbGUuY29tIiwic3ViIjoiMTA0MDI5MjkyODUzMDk5OTc4MjkzIn0."

    runAtInstant(idTokenExpiration.minusSeconds(3)) {
      IdTokenVerifier
        .create[IO](staticKeyProvider(nonGoogleKeys), discovery, jsonSupport)
        .verifyAndDecode(tokenWithAlgorithmNone)
        .map {
          case Left(JwtVerificationError(_: JwtEmptySignatureException)) => ()
          case e                                                         => fail(s"expected JwtEmptySignatureException, got $e")
        }
    }
  }

  test("Algorithm: none and random signature") {
    val tokenWithAlgorithmNone =
      "eyJhbGciOiJub25lIiwia2lkIjoiMTFlMDNmMzliOGQzMDBjOGM5YTFiODAwZGRlYmZjZmRlNDE1MmMwYyIsInR5cCI6IkpXVCJ9Cg.eyJhdWQiOiJodHRwczovL2V4YW1wbGUuY29tL3BhdGgiLCJhenAiOiJpbnRlZ3JhdGlvbi10ZXN0c0BjaGluZ29yLXRlc3QuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLCJlbWFpbCI6ImludGVncmF0aW9uLXRlc3RzQGNoaW5nb3ItdGVzdC5pYW0uZ3NlcnZpY2VhY2NvdW50LmNvbSIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJleHAiOjE1ODc2Mjk4ODgsImlhdCI6MTU4NzYyNjI4OCwiaXNzIjoiaHR0cHM6Ly9hY2NvdW50cy5nb29nbGUuY29tIiwic3ViIjoiMTA0MDI5MjkyODUzMDk5OTc4MjkzIn0.OZUxLojD0DddF9Phg63HQg3R6xvr2Gp3T3msRn09bHvaSUmr_SmMFrIAACiKHHJuQ43eZq9Qvc4ICCMrBwQfVV2FcOXffMQEA6SgTJxRcfSsfhdXX3QDJRGK27x0ynsarcWFrw9TefJyt_gPhhhE0yAzrxCHDPz0LRe8NCv4OvKnw9LZujF5P5k_AxduWTQJuNzHJGsx38E2NLW9SK93KbODZEzCX8YDkddfRfR_LZl2FciRsY6JoHtucqP5KMCFvSJBkfYqQGESeW8EUMxhBH8UrP1pcDD-6u7WXHM5bguC0rrGY6UPvRm3uZMcSYhyOnapC8f0zJBVXGyl9J5dWw"

    runAtInstant(idTokenExpiration.minusSeconds(3)) {
      IdTokenVerifier
        .create[IO](staticKeyProvider(nonGoogleKeys), discovery, jsonSupport)
        .verifyAndDecode(tokenWithAlgorithmNone)
        .map {
          case Left(JwtVerificationError(_: JwtEmptyAlgorithmException)) => ()
          case e                                                         => fail(s"expected JwtEmptyAlgorithmException, got $e")
        }
    }
  }
  test("Unsupported algorithm (HS256)") {
    val tokenWithHs256Algorithm =
      "eyJhbGciOiJIUzI1NiIsImtpZCI6IjExZTAzZjM5YjhkMzAwYzhjOWExYjgwMGRkZWJmY2ZkZTQxNTJjMGMiLCJ0eXAiOiJKV1QifQ.eyJhdWQiOiJodHRwczovL2V4YW1wbGUuY29tL3BhdGgiLCJhenAiOiJpbnRlZ3JhdGlvbi10ZXN0c0BjaGluZ29yLXRlc3QuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLCJlbWFpbCI6ImludGVncmF0aW9uLXRlc3RzQGNoaW5nb3ItdGVzdC5pYW0uZ3NlcnZpY2VhY2NvdW50LmNvbSIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJleHAiOjE1ODc2Mjk4ODgsImlhdCI6MTU4NzYyNjI4OCwiaXNzIjoiaHR0cHM6Ly9hY2NvdW50cy5nb29nbGUuY29tIiwic3ViIjoiMTA0MDI5MjkyODUzMDk5OTc4MjkzIn0.qlsPByqAEglu1PjkzL29bEx82jR_rNQAickLq-erSMw"

    runAtInstant(idTokenExpiration.minusSeconds(3)) {
      IdTokenVerifier
        .create[IO](staticKeyProvider(nonGoogleKeys), discovery, jsonSupport)
        .verifyAndDecode(tokenWithHs256Algorithm)
        .map {
          case Left(JwtVerificationError(_: JwtValidationException)) => ()
          case e                                                     => fail(s"expected JwtValidationException, got $e")
        }
    }
  }

  test("Every verification calls config") {
    runAtInstant(idTokenExpiration.minusSeconds(3)) {
      CacheMock
        .rotateData[IO, OpenIdConfig](
          NonEmptyVector.of(
            config.OpenIdConfig(issuer = Issuer("A"), jwksUri = ""),
            config.OpenIdConfig(issuer = Issuer("B"), jwksUri = "")
          )
        )
        .flatMap { rotatingCache =>
          val rotatingDiscovery =
            OpenIdConnectDiscovery
              .instance[IO](Location.unsafeCreate("https://doesnt-matter"))(HttpTransportMock.const("", ""), jsonSupport, rotatingCache)

          val verifier =
            IdTokenVerifier
              .create[IO](staticKeyProvider(googleKeys), rotatingDiscovery, jsonSupport)

          (1 to 4)
            .toList
            .traverse { _ =>
              verifier
                .verifyAndDecode(rawIdToken)
            }
            .map { result =>
              assertEquals(
                result,
                List(
                  Left(UnexpectedIssuer(Issuer("https://accounts.google.com"), Issuer("A"))),
                  Left(UnexpectedIssuer(Issuer("https://accounts.google.com"), Issuer("B"))),
                  Left(UnexpectedIssuer(Issuer("https://accounts.google.com"), Issuer("A"))),
                  Left(UnexpectedIssuer(Issuer("https://accounts.google.com"), Issuer("B")))
                )
              )
            }

        }

    }
  }

  def runAtInstant[A](@unused instant: Instant)(program: IO[A]): IO[A] =
    TestControl.executeEmbed(IO.sleep(instant.toFiniteDuration) *> program)
}

object IdTokenVerifierTest {

  def staticKeyProvider[F[_]: Applicative](keys: Map[KeyId, PublicKey]): PublicKeyProvider[F] =
    new PublicKeyProvider[F] {
      override def getKey(keyId: KeyId): F[Either[PublicKeyProvider.Error, PublicKey]] =
        keys.get(keyId).toRight(PublicKeyProvider.Error.CouldNotFindPublicKey(keyId)).leftWiden[PublicKeyProvider.Error].pure[F]

      override def getAllKeys: F[Either[PublicKeyProvider.Error, KeyMap]] =
        keys.asRight[PublicKeyProvider.Error].pure[F]

    }

  private object TestValues {
    private val keyFactory = KeyFactory.getInstance("RSA")

    val googleKeys: Map[KeyId, PublicKey] =
      Map(
        "f9d97b4cae90bcd76aeb20026f6b770cac221783" -> "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAya/7gVJrvqFp5xfYPOco8gBLY38kQDlTlT6ueHtUtbTkRVE1X5tFmPqChnX7wWd2fK7MS4+nclYaGLL7IvJtN9tjrD0h/3/HvnrRZTaVyS+yfWqCQDRq/0VW1LBEygwYRqbO2T0lOocTY+5qUosDvJfe+o+lQYMH7qtDAyiq9XprVzKYTfS545BTECXi0he9ikJl5Q/RAP1BZoaip8F0xX5Y/60G90VyXFWuy16nm5ASW8fwqzdn1lL/ogiO1LirgBFFEXz/t4PwmjWzfQwkoKv4Ab/l9u2FdAoKtFH2CwKaGB8hatIK3bOAJJgRebeU3w6Ah3gxRfi8HWPHbAGjtwIDAQAB",
        "85828c59284a69b54b27483e487c3bd46cd2a2b3" -> "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzMHxWuxztMKXdBhv3rImlUvW/yp6nO03cVXPyA0Vyq0+M7LfOJJIF+OdNoRGdsFPHVKCFoo6qGhR8rBCmMxA4fM+Ubk5qKuUqCN9eP3yZJq8Cw9tUrt/qh7uW+qfMr0upcyeSHhC/zW1lTGs5sowDorKN/jQ1Sfh9hfBxfc8T7dQAAgEqqMcE3u+2J701jyhJz0pvurCfziiB3buY6SGREhBQwNwpnQjt/lE2U4km8FS0woPzt0ccE3zsGL2qM+LWZbOm9aXquSnqNJLt3tGVvShnev+GiJ1XfQ3EWm0f4w0TX9fTOkxstl0vo/vW/FjGQ0D1pXSjqb7n+hAdXwc9wIDAQAB",
        "bbd2ac7c4c5eb8adc8eeffbc8f5a2dd6cf7545e4" -> "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAy930dtGTeMG52IPsKmMuEpPHLaxuYQlduZd6BqFVjc2+UFZR8fNqtnYzAjbXWJD/Tqxgdlj/MW4vogvX4sHwVpZONvdyeGoIyDQtis6iuGQhQamV85F/JbrEUnEw3QCO87Liz5UXG6BK2HRyPhDfMex1/tO0ROmySLFdCTS17D0wah71Ibpi0gI8LUi6kzVRjYDIC1oE+iK3Y9s88Bi4ZGYJxXAbnNwbwVkGOKCXja9k0jjBGRxZD+4KDuf493lFOOEGSLDA2Qp9rDqrURP12XYgvf/zJx/kSDipnr0gL6Vz2n3H4+XN4tA45zuzRkHoE7+XexPq+tv7kQ8pSjY2uQIDAQAB"
      )
        .fmap(encodedString => new X509EncodedKeySpec(Base64.getDecoder.decode(encodedString)))
        .fmap(keyFactory.generatePublic)

    val nonGoogleKeys: Map[KeyId, PublicKey] =
      Map(
        "11e03f39b8d300c8c9a1b800ddebfcfde4152c0c" -> "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAsqimgNufhItNGnmuhttmAHQ3jBitVccH6O96zZuc868u5cUmW1rDItaJ7LP83i5gQtPDXLVJ0t4r80HcytBMOllTQtsck09Ck9X2BM7TN0xaIdZfCIGaEpzn1wV00da7qVdhR0Wf4uaJD46JKOXrQU1RWaeYcqFQxUfQr4acJrfBc1FA+Q9uPpMrFqi06ZmxuycyYmsjBlF/mjiCpX1m3UfoNzGh22nkWTaAL4BGVEjW7TjdgdiPmG8NAVTHPR52tFZbLk3ss/9ob6FBXAr+L+fJCkhAgDC3RE7TSAlkN0oaRLVAT9dmmunf2MoVHucLSgEEc9z+3JXZyK3MqDiSdQIDAQAB"
      )
        .fmap(encodedString => new X509EncodedKeySpec(Base64.getDecoder.decode(encodedString)))
        .fmap(keyFactory.generatePublic)

    val rawIdToken =
      "eyJhbGciOiJSUzI1NiIsImtpZCI6ImY5ZDk3YjRjYWU5MGJjZDc2YWViMjAwMjZmNmI3NzBjYWMyMjE3ODMiLCJ0eXAiOiJKV1QifQ.eyJhdWQiOiJodHRwczovL2V4YW1wbGUuY29tL3BhdGgiLCJhenAiOiJpbnRlZ3JhdGlvbi10ZXN0c0BjaGluZ29yLXRlc3QuaWFtLmdzZXJ2aWNlYWNjb3VudC5jb20iLCJlbWFpbCI6ImludGVncmF0aW9uLXRlc3RzQGNoaW5nb3ItdGVzdC5pYW0uZ3NlcnZpY2VhY2NvdW50LmNvbSIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJleHAiOjE1ODc2Mjk4ODgsImlhdCI6MTU4NzYyNjI4OCwiaXNzIjoiaHR0cHM6Ly9hY2NvdW50cy5nb29nbGUuY29tIiwic3ViIjoiMTA0MDI5MjkyODUzMDk5OTc4MjkzIn0.Pj4KsJh7riU7ZIbPMcHcHWhasWEcbVjGP4yx_5E0iOpeDalTdri97E-o0dSSkuVX2FeBIgGUg_TNNgJ3YY97T737jT5DUYwdv6M51dDlLmmNqlu_P6toGCSRC8-Beu5gGmqS2Y82TmpHH9Vhoh5PsK7_rVHk8U6VrrVVKKTWm_IzTFhqX1oYKPdvfyaNLsXPbCt_NFE0C3DNmFkgVhRJu7LtzQQN-ghaqd3Ga3i6KH222OEI_PU4BUTvEiNOqRGoMlT_YOsyFN3XwqQ6jQGWhhkArL1z3CG2BVQjHTKpgVsRyy_H6WTZiju2Q-XWobgH-UPSZbyymV8-cFT9XKEtZQ"

    val idTokenExpiration = Instant.parse("2020-04-23T08:18:08Z")

    val rawIdTokenClaims =
      List(
        "{\"aud\":\"https://example.com/path\",\"azp\":\"integration-tests@chingor-test.iam.gserviceaccount.com\",\"email\":\"integration-tests@chingor-test.iam.gserviceaccount.com\",\"email_verified\":true,\"exp\":1587629888,\"iat\":1587626288,\"iss\":\"https://accounts.google.com\",\"sub\":\"104029292853099978293\"}",
        "{\"aud\":\"https://example.com/path\",\"azp\":\"integration-tests@chingor-test.iam.gserviceaccount.com\",\"email\":\"integration-tests@chingor-test.iam.gserviceaccount.com\",\"email_verified\":true,\"exp\":1587629888,\"iat\":1587626288,\"iss\":\"https://thisisnotgoogle.com\",\"sub\":\"104029292853099978293\"}"
      )

    val clientId = ClientId("https://example.com/path")

    val decodedIdTokenClaims =
      List(
        IdTokenClaims(
          issuer = Issuer("https://accounts.google.com"),
          subject = Subject("104029292853099978293"),
          audience = NonEmptySet.of(Audience("https://example.com/path")),
          expiration = idTokenExpiration,
          issuedAt = Instant.parse("2020-04-23T07:18:08Z")
        ),
        IdTokenClaims(
          issuer = Issuer("https://thisisnotgoogle.com"),
          subject = Subject("104029292853099978293"),
          audience = NonEmptySet.of(Audience("https://example.com/path")),
          expiration = idTokenExpiration,
          issuedAt = Instant.parse("2020-04-23T07:18:08Z")
        )
      )

    val rawJwtHeaders =
      List(
        """{"alg":"RS256","kid":"f9d97b4cae90bcd76aeb20026f6b770cac221783","typ":"JWT"}""",
        """{"alg":"RS256","kid":"11e03f39b8d300c8c9a1b800ddebfcfde4152c0c","typ":"JWT"}""",
        """{"alg":"none","kid":"11e03f39b8d300c8c9a1b800ddebfcfde4152c0c","typ":"JWT"}""",
        """{"alg":"HS256","kid":"11e03f39b8d300c8c9a1b800ddebfcfde4152c0c","typ":"JWT"}"""
      )

    val decodedJwtHeaders =
      List(
        JwtHeader(keyId = Some("f9d97b4cae90bcd76aeb20026f6b770cac221783")),
        JwtHeader(keyId = Some("11e03f39b8d300c8c9a1b800ddebfcfde4152c0c")),
        JwtHeader(keyId = Some("11e03f39b8d300c8c9a1b800ddebfcfde4152c0c")),
        JwtHeader(keyId = Some("11e03f39b8d300c8c9a1b800ddebfcfde4152c0c"))
      )

  }

}
