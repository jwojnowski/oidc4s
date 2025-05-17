package me.wojnowski.oidc4s

import scala.concurrent.duration.FiniteDuration

import java.time.Instant
import java.util.concurrent.TimeUnit

object TimeUtils {

  implicit class InstantToFiniteDuration(instant: Instant) {
    def toFiniteDuration: FiniteDuration = FiniteDuration(instant.toEpochMilli, TimeUnit.MILLISECONDS)
  }

}
