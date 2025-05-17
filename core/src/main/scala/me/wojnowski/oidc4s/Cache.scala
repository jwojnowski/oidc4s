package me.wojnowski.oidc4s

import cats.Applicative
import cats.effect.Clock
import cats.effect.Ref
import cats.effect.Sync
import cats.syntax.all._

import scala.concurrent.duration.DAYS
import scala.concurrent.duration.FiniteDuration

import java.time.Instant

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

  case class Entry[A](value: A, expiresAt: Instant)
}
