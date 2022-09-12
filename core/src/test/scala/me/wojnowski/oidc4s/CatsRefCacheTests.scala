package me.wojnowski.oidc4s

import cats.effect.IO
import cats.effect.testkit.TestControl
import me.wojnowski.oidc4s.cache.Cache
import munit.CatsEffectSuite

import scala.concurrent.duration.DurationInt
import cats.syntax.all._

class CatsRefCacheTests extends CatsEffectSuite {
  private val expiration = 10.minutes

  val value = "A"

  test("Empty cache returns None") {
    for {
      cache  <- Cache.catsRef[IO, String]()
      result <- cache.get
    } yield assertEquals(result, None)
  }

  test("Cache returns the value if entry is not stale") {
    for {
      cache  <- Cache.catsRef[IO, String]()
      _      <- cache.put(value, expiresIn = expiration.some)
      result <- cache.get
    } yield assertEquals(result, Some(value))
  }

  test("Cache with expired entry returns None") {
    TestControl.executeEmbed {
      for {
        cache  <- Cache.catsRef[IO, String](defaultExpiration = 10.minutes)
        _      <- cache.put(value, expiresIn = 5.minutes.some)
        _      <- IO.sleep(6.minutes)
        result <- cache.get
      } yield assertEquals(result, None)
    }
  }

  test("Cache uses default expiration if entry doesn't have one") {
    TestControl.executeEmbed {
      for {
        cache              <- Cache.catsRef[IO, String](defaultExpiration = 10.minutes)
        _                  <- cache.put(value, expiresIn = None)
        _                  <- IO.sleep(9.minutes)
        intermediateResult <- cache.get
        _                  <- IO.sleep(5.minutes)
        result             <- cache.get
      } yield {
        assertEquals(intermediateResult, Some(value))
        assertEquals(result, None)
      }
    }
  }

  test("Cache does not accept a new value, if the new value has shorter expiration time") {
    TestControl.executeEmbed {
      val firstValue = "X"
      val secondValue = "Y"

      for {
        cache   <- Cache.catsRef[IO, String](defaultExpiration = 10.minutes)
        _       <- cache.put(firstValue, expiresIn = 25.minutes.some)
        _       <- cache.put(secondValue, expiresIn = 10.minutes.some)
        _       <- cache.put(secondValue, expiresIn = None)
        result1 <- cache.get
        _       <- IO.sleep(24.minutes)
        result2 <- cache.get
        _       <- IO.sleep(2.minutes)
        result3 <- cache.get
      } yield {
        assertEquals(result1, Some(firstValue))
        assertEquals(result2, Some(firstValue))
        assertEquals(result3, None)
      }
    }
  }
}
