package me.wojnowski.oidc4s

import cats.data.NonEmptySet
import cats.effect.IO
import cats.effect.testkit.TestControl
import cats.implicits._
import me.wojnowski.oidc4s.IdTokenClaims.Audience
import me.wojnowski.oidc4s.IdTokenClaims.Subject
import me.wojnowski.oidc4s.PropertyIdTokenVerifierTest._
import me.wojnowski.oidc4s.mocks.JsonSupportMock
import munit.CatsEffectSuite
import munit.ScalaCheckEffectSuite
import org.scalacheck.Test.Parameters
import org.scalacheck.Gen
import org.scalacheck.Test
import org.scalacheck.effect.PropF.forAllF
import pdi.jwt.Jwt
import pdi.jwt.JwtClaim

import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import scala.concurrent.duration.DurationLong
import scala.concurrent.duration.FiniteDuration

class PropertyIdTokenVerifierTest extends CatsEffectSuite with ScalaCheckEffectSuite {

  override val munitIOTimeout: FiniteDuration = 90.seconds

  override protected def scalaCheckTestParameters: Test.Parameters = Parameters.default.withMinSuccessfulTests(30)

  test("Verification succeeds given correct public key") {
    forAllF(keyIdGen, matchingKeyPairGen, clockGen, algorithmGen, clientIdGen) {
      case (keyId, (privateKey, publicKey), clock, algorithm, clientId) =>
        TestControl.executeEmbed {
          val (rawJwt, verifier, subject) = prepareJwtAndVerifier(keyId, privateKey, publicKey, clock, algorithm, clientId)

          for {
            _      <- IO.sleep(clock.instant.getEpochSecond.seconds)
            result <- verifier.verify(rawJwt, clientId)
          } yield assertEquals(result, Right(subject))
        }
    }
  }

  test("Verification fails given incorrect public key") {
    forAllF(keyIdGen, mismatchedKeyPairGen, clockGen, algorithmGen, clientIdGen) {
      case (keyId, (privateKey, publicKey), clock, algorithm, clientId) =>
        TestControl.executeEmbed {
          val (rawJwt, verifier, _) = prepareJwtAndVerifier(keyId, privateKey, publicKey, clock, algorithm, clientId)

          for {
            _      <- IO.sleep(clock.instant.getEpochSecond.seconds)
            result <- verifier.verify(rawJwt, clientId)
          } yield assertEquals(result, Left(IdTokenVerifier.Error.InvalidSignature))
        }
    }
  }

  private def prepareJwtAndVerifier(
    keyId: UUID,
    privateKey: PrivateKey,
    publicKey: PublicKey,
    clock: java.time.Clock,
    algorithm: Algorithm,
    clientId: ClientId
  ): (String, IdTokenVerifier[IO], Subject) = {
    val publicKeyProvider = PublicKeyProvider.static[IO](Map(keyId.toString -> publicKey))
    val issuer = Issuer("https://example.com")
    val subject = Subject("user-id")

    val issuedAt = clock.instant()
    val expiresAt = issuedAt.plusSeconds(600)

    val issuedAtSeconds = issuedAt.getEpochSecond
    val expiresAtSeconds = expiresAt.getEpochSecond

    val rawClaims = s"""{"sub":"${subject.value}","aud":["$clientId"],"exp":$expiresAtSeconds,"iat":$issuedAtSeconds}"""
    val claims = IdTokenClaims(issuer, subject, NonEmptySet.of(Audience(clientId.value)), expiresAt, issuedAt)

    val rawHeader = s"""{"alg":"${algorithm.name}","kid":"$keyId"}"""
    val header = JoseHeader(keyId.toString, algorithm)

    val verifier =
      IdTokenVerifier.static(
        publicKeyProvider,
        issuer,
        JsonSupportMock.instance(Map(rawClaims -> Right(claims)), Map(rawHeader -> Right(header)))
      )

    val rawJwt =
      Jwt(clock).encode(
        pdi.jwt.JwtHeader(pdi.jwt.JwtAlgorithm.fromString(algorithm.name).some, keyId = keyId.toString.some),
        JwtClaim(rawClaims),
        privateKey
      )

    (rawJwt, verifier, subject)
  }

}

object PropertyIdTokenVerifierTest {
  val keyIdGen: Gen[UUID] = Gen.uuid

  private def keyPairGen(keySize: Int) = Gen.delay {
    val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
    keyPairGenerator.initialize(keySize)
    val keyPair = keyPairGenerator.generateKeyPair()
    Gen.const((keyPair.getPrivate, keyPair.getPublic))
  }

  val matchingKeyPairGen: Gen[(PrivateKey, PublicKey)] = Gen.oneOf(2048, 4096).flatMap { keySize =>
    keyPairGen(keySize)
  }

  val mismatchedKeyPairGen: Gen[(PrivateKey, PublicKey)] = Gen.oneOf(2048, 4096).flatMap { keySize =>
    keyPairGen(keySize).flatMap { case firstPair @ (firstPrivateKey, _) =>
      keyPairGen(keySize).suchThat(_ != firstPair).map { case (_, secondPublicKey) =>
        (firstPrivateKey, secondPublicKey)
      }
    }
  }

  val clockGen: Gen[Clock] =
    Gen.choose(2137L, 1696369856L).map { seconds =>
      java.time.Clock.fixed(Instant.ofEpochSecond(seconds), ZoneId.of("UTC"))
    }

  val algorithmGen: Gen[Algorithm] =
    Gen.oneOf(Algorithm.supportedAlgorithms.toSortedSet)

  val clientIdGen: Gen[ClientId] =
    Gen.choose(3, 24).flatMap(length => Gen.listOfN(length, Gen.alphaNumChar).map(_.mkString)).map(ClientId.apply)

}
