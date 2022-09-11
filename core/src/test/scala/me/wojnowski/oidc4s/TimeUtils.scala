package me.wojnowski.oidc4s

import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

object TimeUtils {

  implicit class InstantToFiniteDuration(instant: Instant) {
    def toFiniteDuration: FiniteDuration = FiniteDuration(instant.toEpochMilli, TimeUnit.MILLISECONDS)
  }

}
