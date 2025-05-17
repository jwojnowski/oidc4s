package me.wojnowski.oidc4s.impure

import cats.Applicative
import cats.effect.Clock

import scala.concurrent.duration.FiniteDuration

import java.util.concurrent.TimeUnit

package object implicits {

  implicit def clock[F[_]](implicit F: Applicative[F]): Clock[F] = new Clock[F] {
    override def applicative: Applicative[F] = F

    override def monotonic: F[FiniteDuration] = F.pure(FiniteDuration(System.nanoTime(), TimeUnit.NANOSECONDS))

    override def realTime: F[FiniteDuration] = F.pure(FiniteDuration(System.currentTimeMillis(), TimeUnit.MILLISECONDS))
  }

}
