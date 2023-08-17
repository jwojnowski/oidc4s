package me.wojnowski.oidc4s

import cats.Id
import cats.data.NonEmptyVector
import cats.effect.IO
import cats.effect.Ref
import cats.syntax.all._
import me.wojnowski.oidc4s.PublicKeyProvider.JsonWebKey
import me.wojnowski.oidc4s.PublicKeyProvider.JsonWebKeySet
import me.wojnowski.oidc4s.PublicKeyProvider.KeyId
import me.wojnowski.oidc4s.PublicKeyProviderTest.CountAndState
import me.wojnowski.oidc4s.config.Location
import me.wojnowski.oidc4s.config.OpenIdConfig
import me.wojnowski.oidc4s.config.OpenIdConnectDiscovery
import me.wojnowski.oidc4s.mocks.CacheMock
import me.wojnowski.oidc4s.mocks.HttpTransportMock
import me.wojnowski.oidc4s.mocks.JsonSupportMock
import me.wojnowski.oidc4s.transport.Transport
import munit.CatsEffectSuite

import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

class PublicKeyProviderTest extends CatsEffectSuite {
  private val keyFactory = KeyFactory.getInstance("RSA")

  private val jsonWebKeySet =
    JsonWebKeySet(
      List(
        JsonWebKey(
          modulus =
            "jN4xvvGtTeXxq5DZxQxBdafPZAfXn6uowE1VsVXRaSo28GAizL0OdErMui028K3pLN1XkThebJruh7SSadG3H7WJfpxf4wyCgj1ofbRIhbjjKcPqO86Lo_Uekzsv5MeW4Q2ZOvZiJkLnp3zFnFKaeBV0P408k2HbGnHS6LEcDqDWA7G-TmE-TZIoB6HZ0Q7dN3oFYJ831NZj3IyNRC9lzNaG-S00AEvKNO-3J59qig09Z_M9yuHlU1WI-BNO8wyx-5kZFe_px6m7QQ95y9v9EZWeIKMCQkomkXYhLOa7GQT9ITh5uINeRqh4rIzY1z5uAHDkgIqHn1Ztpw1O47jOew",
          publicExponent = "AQAB",
          keyId = "402f305b70581329ff289b5b3a67283806eca893"
        ),
        JsonWebKey(
          modulus =
            "yJdNun_DT8_krjOUFMk4UPb7KgOyoN2EIHVL77LFLUlzFwOLon1pEceYcWffNQnjdtzDCN5-q6DxlIiJyDgQhPPMpJzMcpZceo0tKd-Ve1RLEUVcbnbjyZ-inrxVWfYTOuWTsutt7EylFDIMfw1Dh14IccFG5loyLdtZX2yejhXmJzMCxTISE_lCxCIiIqu5filfc3AnnyNb66Mv_oyK5z22pc9f-dFAmT3e5IXA-0UkrEVtLl7lRGmWdBkAkEWzhh17aQ0BynxpcTX5efGyr2b5ktUObCNdKMwNE4_Berz4l7_Oz6-gWDlyjbROrHKx0B27SFHdtNHbYARJsfVsjw",
          publicExponent = "AQAB",
          keyId = "1727b6b49402b9cf95be4e8fd38aa7e7c11644b1"
        )
      )
    )

  val nonExistentKeyId = "3b2748385828c5e4284a69b52b387c3bd46cd2a9"

  val keyId1: KeyId = "402f305b70581329ff289b5b3a67283806eca893"
  val keyId2: KeyId = "1727b6b49402b9cf95be4e8fd38aa7e7c11644b1"
  val keyId3: KeyId = "bbd2ac7c4c5eb8adc8eeffbc8f5a2dd6cf7545e4"

  val keys: Map[KeyId, PublicKey] =
    Map(
      keyId1 -> "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAjN4xvvGtTeXxq5DZxQxBdafPZAfXn6uowE1VsVXRaSo28GAizL0OdErMui028K3pLN1XkThebJruh7SSadG3H7WJfpxf4wyCgj1ofbRIhbjjKcPqO86Lo/Uekzsv5MeW4Q2ZOvZiJkLnp3zFnFKaeBV0P408k2HbGnHS6LEcDqDWA7G+TmE+TZIoB6HZ0Q7dN3oFYJ831NZj3IyNRC9lzNaG+S00AEvKNO+3J59qig09Z/M9yuHlU1WI+BNO8wyx+5kZFe/px6m7QQ95y9v9EZWeIKMCQkomkXYhLOa7GQT9ITh5uINeRqh4rIzY1z5uAHDkgIqHn1Ztpw1O47jOewIDAQAB",
      keyId2 -> "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAyJdNun/DT8/krjOUFMk4UPb7KgOyoN2EIHVL77LFLUlzFwOLon1pEceYcWffNQnjdtzDCN5+q6DxlIiJyDgQhPPMpJzMcpZceo0tKd+Ve1RLEUVcbnbjyZ+inrxVWfYTOuWTsutt7EylFDIMfw1Dh14IccFG5loyLdtZX2yejhXmJzMCxTISE/lCxCIiIqu5filfc3AnnyNb66Mv/oyK5z22pc9f+dFAmT3e5IXA+0UkrEVtLl7lRGmWdBkAkEWzhh17aQ0BynxpcTX5efGyr2b5ktUObCNdKMwNE4/Berz4l7/Oz6+gWDlyjbROrHKx0B27SFHdtNHbYARJsfVsjwIDAQAB",
      keyId3 -> "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAy930dtGTeMG52IPsKmMuEpPHLaxuYQlduZd6BqFVjc2+UFZR8fNqtnYzAjbXWJD/Tqxgdlj/MW4vogvX4sHwVpZONvdyeGoIyDQtis6iuGQhQamV85F/JbrEUnEw3QCO87Liz5UXG6BK2HRyPhDfMex1/tO0ROmySLFdCTS17D0wah71Ibpi0gI8LUi6kzVRjYDIC1oE+iK3Y9s88Bi4ZGYJxXAbnNwbwVkGOKCXja9k0jjBGRxZD+4KDuf493lFOOEGSLDA2Qp9rDqrURP12XYgvf/zJx/kSDipnr0gL6Vz2n3H4+XN4tA45zuzRkHoE7+XexPq+tv7kQ8pSjY2uQIDAQAB"
    ).fmap(encodedString => KeyUtils.parsePublicPemKey(encodedString).toOption.get)

  val jwksUrl = "http://appleid.apple.com/.well-known/openid-configuration"

  private val transport = HttpTransportMock.const[Id](jwksUrl, "")

  private val jsonSupport = JsonSupportMock.instance(jsonWebKeySetTranslations = { _ => jsonWebKeySet })

  val discovery: OpenIdConnectDiscovery[Id] = OpenIdConnectDiscovery.static[Id](OpenIdConfig(issuer = Issuer(""), jwksUrl))

  val keyProvider: PublicKeyProvider[Id] = PublicKeyProvider.discovery[Id](discovery)(transport, jsonSupport)

  test("First key") {
    val key1 = keyProvider.getKey(keyId1)

    assertEquals(key1, Right(keys.apply(keyId1)))
  }

  test("Second key") {
    val key2 = keyProvider.getKey(keyId2)
    assertEquals(key2, Right(keys.apply(keyId2)))
  }

  test("Non-existent key") {
    val nonExistentKey = keyProvider.getKey(nonExistentKeyId)

    assertEquals(nonExistentKey, Left(PublicKeyProvider.Error.CouldNotFindPublicKey(nonExistentKeyId)))
  }

  test("All keys") {
    val all = keyProvider.getAllKeys

    assertEquals(
      all,
      Right(
        Map(
          keyId1 -> keys.apply(keyId1),
          keyId2 -> keys.apply(keyId2)
        )
      )
    )
  }

  test("PublicKeyProvider uses discovery for every call") {
    val ioTransport = HttpTransportMock.const[IO]("", "", uri => Transport.Error.UnexpectedResponse(404, s"Expected $uri".some))

    CacheMock
      .rotateData[IO, OpenIdConfig](
        NonEmptyVector.of(
          config.OpenIdConfig(issuer = Issuer(""), jwksUri = "https://a"),
          config.OpenIdConfig(issuer = Issuer(""), jwksUri = "https://b")
        )
      )
      .flatMap { rotatingCache =>
        val rotatingDiscovery =
          OpenIdConnectDiscovery
            .instance[IO](Location.unsafeCreate("https://spanish-inquisition"))(
              HttpTransportMock.const("nobody-expects-spanish-inquisition", "a"),
              jsonSupport,
              rotatingCache
            )

        val publicKeyProvider = PublicKeyProvider.discovery(rotatingDiscovery)(ioTransport, jsonSupport)

        (1 to 2)
          .toList
          .traverse { _ =>
            publicKeyProvider.getAllKeys
          }
          .map { result =>
            assertEquals(
              result,
              List(
                Left(
                  PublicKeyProvider
                    .Error
                    .CouldNotFetchKeys(
                      Transport.Error.UnexpectedResponse(404, s"Expected https://a".some)
                    )
                ),
                Left(
                  PublicKeyProvider
                    .Error
                    .CouldNotFetchKeys(
                      Transport.Error.UnexpectedResponse(404, s"Expected https://b".some)
                    )
                )
              )
            )
          }

      }
  }

  test("Cached provider returns the key without a new call") {
    withRecordingKeyProvider { case (keyProvider, ref) =>
      for {
        _     <- ref.update(_.copy(keys = keys))
        cache <- Cache.catsRef[IO, PublicKeyProvider.KeyMap]()
        cachedKeyProvider = PublicKeyProvider.cached[IO](keyProvider, cache)
        _     <- cachedKeyProvider.getKey(keyId1)
        _     <- cachedKeyProvider.getKey(keyId1)
        state <- ref.get
      } yield assertEquals(state.requestCount, 1)
    }
  }

  test("Cached provider always gets a fresh response for getAll") {
    withRecordingKeyProvider { case (keyProvider, ref) =>
      for {
        _     <- ref.update(_.copy(keys = keys))
        cache <- Cache.catsRef[IO, PublicKeyProvider.KeyMap]()
        cachedKeyProvider = PublicKeyProvider.cached[IO](keyProvider, cache)
        _     <- cachedKeyProvider.getAllKeys
        _     <- cachedKeyProvider.getAllKeys
        state <- ref.get
      } yield assertEquals(state.requestCount, 2)
    }
  }

  test("Cached provider refreshes the cache when a new key is requested") {
    withRecordingKeyProvider { case (keyProvider, ref) =>
      for {
        _     <- ref.update(_.copy(keys = keys.slice(0, 2)))
        cache <- Cache.catsRef[IO, PublicKeyProvider.KeyMap]()
        cachedKeyProvider = PublicKeyProvider.cached[IO](keyProvider, cache)
        _     <- cachedKeyProvider.getKey(keyId1) // calls: 1
        _     <- cachedKeyProvider.getKey(keyId1) // calls: 0
        _     <- cachedKeyProvider.getKey(keyId2) // calls: 0
        _     <- cachedKeyProvider.getKey(keyId2) // calls: 0
        _     <- ref.update(_.copy(keys = keys.slice(1, 3)))
        _     <- cachedKeyProvider.getKey(keyId2) // calls: 0
        x     <- cachedKeyProvider.getKey(keyId3) // calls: 1
        _     <- cachedKeyProvider.getKey(keyId3) // calls: 0
        state <- ref.get
      } yield assertEquals(state.requestCount, 2)
    }
  }

  private def withRecordingKeyProvider[A](f: (PublicKeyProvider[IO], Ref[IO, CountAndState]) => IO[A]): IO[A] =
    for {
      ref    <- Ref[IO].of(CountAndState(0, Map.empty))
      result <- f(
                  new PublicKeyProvider[IO] {

                    override def getKey(keyId: KeyId): IO[Either[PublicKeyProvider.Error, PublicKey]] =
                      ref
                        .modify { state =>
                          (
                            state.copy(requestCount = state.requestCount + 1),
                            state.keys.get(keyId).toRight(PublicKeyProvider.Error.CouldNotFindPublicKey(keyId))
                          )
                        }

                    override def getAllKeys: IO[Either[PublicKeyProvider.Error, Map[KeyId, PublicKey]]] =
                      ref
                        .modify { state =>
                          (
                            state.copy(requestCount = state.requestCount + 1),
                            Right(state.keys)
                          )
                        }

                  },
                  ref
                )
    } yield result

}

object PublicKeyProviderTest {
  private case class CountAndState(requestCount: Int, keys: PublicKeyProvider.KeyMap)
}
