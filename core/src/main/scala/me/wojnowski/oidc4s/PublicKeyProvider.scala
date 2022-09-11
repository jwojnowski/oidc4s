package me.wojnowski.oidc4s

import cats.Functor
import cats.Monad
import cats.data.EitherT
import me.wojnowski.oidc4s.PublicKeyProvider.KeyId

import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import scala.util.Try
import cats.syntax.all._
import me.wojnowski.oidc4s.PublicKeyProvider.Error
import me.wojnowski.oidc4s.PublicKeyProvider.Error.CouldNotDiscoverConfig
import me.wojnowski.oidc4s.cache.Cache

trait PublicKeyProvider[F[_]] {
  def getKey(keyId: KeyId): F[Either[PublicKeyProvider.Error, PublicKey]]

  def getAllKeys: F[Either[PublicKeyProvider.Error, PublicKeyProvider.KeyMap]]
}

object PublicKeyProvider {
  type KeyId = String

  // TODO rename Keys?
  type KeyMap = Map[KeyId, PublicKey]

  private val rsaKeyFactory = KeyFactory.getInstance("RSA")

  // TODO think about the arguments
  def jwks[F[_]: Monad](
    discovery: OpenIdConnectDiscovery[F]
  )(
    transport: HttpTransport[F],
    jsonSupport: JsonSupport
  ): PublicKeyProvider[F] =
    new PublicKeyProvider[F] {

      import jsonSupport._

      override def getKey(keyId: KeyId): F[Either[Error, PublicKey]] =
        getAllKeys.map(_.flatMap(_.get(keyId).toRight(Error.CouldNotFindPublicKey(keyId))))

      override def getAllKeys: F[Either[Error, KeyMap]] = {
        for {
          config  <- EitherT(discovery.getConfig).leftMap(CouldNotDiscoverConfig.apply)
          rawJson <- EitherT(
                       transport
                         .get(config.jwksUri)
                     ).leftMap(Error.CouldNotFetchKeys.apply)
          keys    <- EitherT.fromEither {
                       JsonDecoder[JsonWebKeySet]
                         .decode(rawJson.data)
                         .map(_.toPublicKeyMap)
                         .map(_.collect { case (keyId, Right(publicKey)) => keyId -> publicKey })
                         .leftMap(Error.CouldNotDecodeResponse.apply)
                         .leftWiden[Error]
                     }
        } yield keys
      }.value

    }

  def cached[F[_]: Monad](delegate: PublicKeyProvider[F], cache: Cache[F, KeyMap]): PublicKeyProvider[F] =
    new PublicKeyProvider[F] {

      override def getKey(keyId: KeyId): F[Either[Error, PublicKey]] =
        for {
          maybeCachedKeys <- cache.get
          maybeCachedKey = maybeCachedKeys.flatMap(_.get(keyId))
          keyEither       <- maybeCachedKey.fold(refreshAndGet(keyId))(_.asRight[Error].pure[F])
        } yield keyEither

      override def getAllKeys: F[Either[Error, KeyMap]] = delegate.getAllKeys

      private def refreshAndGet(keyId: KeyId): F[Either[Error, PublicKey]] =
        delegate.getAllKeys.flatMap {
          case Right(keys) =>
            cache
              .put(keys, expiresIn = None)
              .as {
                keys.get(keyId).toRight(Error.CouldNotFindPublicKey(keyId))
              }
          case Left(error) =>
            error.asLeft[PublicKey].pure[F]
        }

    }

  case class JsonWebKeySet(keys: List[JsonWebKey]) {
    def toPublicKeyMap: Map[KeyId, Either[Error.CouldNotDecodePublicKey, PublicKey]] = keys.map(key => (key.keyId, key.toPublicKey)).toMap
  }

  case class JsonWebKey(modulus: String, publicExponent: String, keyId: String) {

    def toPublicKey: Either[Error.CouldNotDecodePublicKey, PublicKey] =
      Try {
        val keySpec = new RSAPublicKeySpec(
          new BigInteger(1, Base64.getUrlDecoder.decode(modulus)),
          new BigInteger(1, Base64.getUrlDecoder.decode(publicExponent))
        )

        rsaKeyFactory.generatePublic(keySpec)
      }.toEither.leftMap(Error.CouldNotDecodePublicKey.apply)

  }

  sealed trait Error extends ProductSerializableNoStacktrace

  object Error {
    case class CouldNotDiscoverConfig(cause: OpenIdConnectDiscovery.Error) extends Error

    case class CouldNotFindPublicKey(keyId: KeyId) extends Error

    case class CouldNotDecodePublicKey(cause: Throwable) extends Error

    case class CouldNotDecodeResponse(details: String) extends Error

    case class CouldNotFetchKeys(cause: HttpTransport.Error) extends Error
  }

}
