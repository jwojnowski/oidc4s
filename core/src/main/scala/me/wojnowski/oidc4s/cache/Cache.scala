package me.wojnowski.oidc4s.cache

import cats.Applicative
import cats.Monad
import cats.effect.Clock
import cats.effect.Ref
import cats.effect.Sync
import cats.syntax.all._
import me.wojnowski.oidc4s.OpenIdConfig

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.DAYS
import scala.concurrent.duration.FiniteDuration

trait Cache[F[_], A] {
  def get: F[Option[A]]

  def put(value: A, expiresIn: Option[FiniteDuration]): F[Unit]
}

object Cache {

  val DefaultExpiration: FiniteDuration = FiniteDuration(1, DAYS)

  def noop[F[_]: Applicative, A]: Cache[F, A] = new Cache[F, A] {
    override def get: F[Option[A]] = none[A].pure[F]

    override def put(value: A, expiresIn: Option[FiniteDuration]): F[Unit] = ().pure[F]
  }

  def atomicRef[F[_]: Monad: Clock, A](defaultExpiration: FiniteDuration = DefaultExpiration): Cache[F, A] =
    new Cache[F, A] {
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

  def catsRef[F[_]: Sync, A](defaultExpiration: FiniteDuration = DefaultExpiration): F[Cache[F, A]] =
    Ref.of[F, Option[Entry[A]]](None).map { configRef =>
      new Cache[F, A] {

        override def get: F[Option[A]] =
          for {
            now        <- Clock[F].realTimeInstant
            maybeEntry <- configRef.get
          } yield maybeEntry.filter(_.expiresAt.isAfter(now)).map(_.value)

        override def put(config: A, expiresIn: Option[FiniteDuration]): F[Unit] =
          Clock[F].realTimeInstant.flatMap { now =>
            val expiresAt = now.plusNanos(expiresIn.getOrElse(defaultExpiration).toNanos)
            configRef.updateAndGet {
              case Some(entry) if entry.expiresAt.isAfter(expiresAt) =>
                Some(entry)
              case _                                                 =>
                Some(Entry(config, expiresAt))
            }.void
          }
      }

    }

  private[cache] case class Entry[A](value: A, expiresAt: Instant)
}
