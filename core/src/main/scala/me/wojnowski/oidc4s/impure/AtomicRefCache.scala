package me.wojnowski.oidc4s.impure

import cats.Monad
import cats.effect.Clock
import cats.syntax.all._
import me.wojnowski.oidc4s.Cache
import me.wojnowski.oidc4s.Cache.DefaultExpiration
import me.wojnowski.oidc4s.Cache.Entry

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.FiniteDuration

case class AtomicRefCache[F[_]: Monad: Clock, A](defaultExpiration: FiniteDuration = DefaultExpiration) extends Cache[F, A] {
  // Used directly instead of cats.effect.Ref[], as this particular use
  // does not seem to require Sync[F]
  private val configRef = new AtomicReference[Option[Entry[A]]](None)

  override def get: F[Option[A]] =
    for {
      now <- Clock[F].realTimeInstant
      maybeEntry = configRef.get
    } yield maybeEntry.filter(_.expiresAt.isAfter(now)).map(_.value)

  override def put(config: A, expiresIn: Option[FiniteDuration]): F[Unit] =
    Clock[F].realTimeInstant.map { now =>
      val expiresAt = now.plusNanos(expiresIn.getOrElse(defaultExpiration).toNanos)
      configRef.updateAndGet {
        case Some(entry) if entry.expiresAt.isAfter(expiresAt) =>
          Some(entry)
        case _                                                 =>
          Some(Entry(config, expiresAt))
      }
      ()
    }

}
