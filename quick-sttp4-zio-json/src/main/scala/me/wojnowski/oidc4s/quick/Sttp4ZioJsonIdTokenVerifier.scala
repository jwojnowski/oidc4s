package me.wojnowski.oidc4s.quick

import me.wojnowski.oidc4s.Cache
import me.wojnowski.oidc4s.IdTokenVerifier
import me.wojnowski.oidc4s.PublicKeyProvider
import me.wojnowski.oidc4s.PublicKeyProvider.KeyMap
import me.wojnowski.oidc4s.config.Location
import me.wojnowski.oidc4s.config.OpenIdConfig
import me.wojnowski.oidc4s.config.OpenIdConnectDiscovery
import me.wojnowski.oidc4s.impure.AtomicRefCache
import me.wojnowski.oidc4s.json.ziojson.ZioJsonSupport
import me.wojnowski.oidc4s.transport.sttp4.SttpTransport

import cats.Monad
import cats.effect.kernel.Clock
import cats.effect.kernel.Sync
import cats.syntax.all._

import scala.concurrent.duration.FiniteDuration

import sttp.client4.Backend

object Sttp4ZioJsonIdTokenVerifier {

  def cachedWithCatsRef[F[_]: Sync](
    location: Location,
    defaultExpiration: FiniteDuration = Cache.DefaultExpiration
  )(
    backend: Backend[F]
  ): F[IdTokenVerifier[F]] =
    for {
      configCache    <- Cache.catsRef[F, OpenIdConfig](defaultExpiration)
      publicKeyCache <- Cache.catsRef[F, KeyMap](defaultExpiration)
    } yield cached(location, configCache, publicKeyCache)(backend)

  def cachedWithAtomicRef[F[_]: Monad: Clock](
    location: Location,
    defaultExpiration: FiniteDuration = Cache.DefaultExpiration
  )(
    backend: Backend[F]
  ): IdTokenVerifier[F] = {
    val configCache = AtomicRefCache[F, OpenIdConfig](defaultExpiration)
    val publicKeyProviderCache = AtomicRefCache[F, KeyMap](defaultExpiration)

    cached(location, configCache, publicKeyProviderCache)(backend)
  }

  def cached[F[_]: Monad: Clock](
    location: Location,
    configCache: Cache[F, OpenIdConfig],
    publicKeyCache: Cache[F, PublicKeyProvider.KeyMap]
  )(
    backend: Backend[F]
  ): IdTokenVerifier[F] = {
    val discovery = OpenIdConnectDiscovery.instance[F](location)(SttpTransport.instance(backend), ZioJsonSupport, configCache)

    create(discovery, publicKeyCache.some)(backend)
  }

  def static[F[_]: Monad: Clock](
    config: OpenIdConfig,
    publicKeyCache: Option[Cache[F, PublicKeyProvider.KeyMap]]
  )(
    backend: Backend[F]
  ): IdTokenVerifier[F] =
    create(OpenIdConnectDiscovery.static[F](config), publicKeyCache)(backend)

  def create[F[_]: Monad: Clock](
    discovery: OpenIdConnectDiscovery[F],
    publicKeyCache: Option[Cache[F, KeyMap]]
  )(
    backend: Backend[F]
  ): IdTokenVerifier[F] = {
    val publicKeyProvider = PublicKeyProvider.discovery(discovery)(SttpTransport.instance(backend), ZioJsonSupport)

    IdTokenVerifier
      .discovery[F](
        publicKeyCache.fold(publicKeyProvider)(PublicKeyProvider.cached(publicKeyProvider, _)),
        discovery,
        ZioJsonSupport
      )
  }

}
