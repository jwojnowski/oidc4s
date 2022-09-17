package me.wojnowski.oidc4s.mocks

import cats.data.NonEmptyVector
import cats.effect.Ref
import cats.effect.Sync
import cats.syntax.all._
import me.wojnowski.oidc4s.Cache

import scala.concurrent.duration.FiniteDuration

object CacheMock {

  def rotateData[F[_]: Sync, A](data: NonEmptyVector[A]): F[Cache[F, A]] = Ref[F].of(0L).map { ref =>
    new Cache[F, A] {
      override def get: F[Option[A]] =
        ref.getAndUpdate(_ + 1).map(i => data.get(i % data.size))

      override def put(value: A, expiresIn: Option[FiniteDuration]): F[Unit] = ().pure[F]
    }
  }

}
