package me.wojnowski.oidc4s.json.ziojson

import me.wojnowski.oidc4s.IdTokenClaims.Audience

import cats.data.NonEmptySet

import scala.collection.immutable.SortedSet

import zio.json._

trait AudienceZioJsonDecoder {
  protected implicit val audienceDecoder: JsonDecoder[Audience] =
    JsonDecoder[String].map(Audience.apply)

  protected implicit val audienceNesDecoder: JsonDecoder[NonEmptySet[Audience]] =
    audienceDecoder.map(NonEmptySet.one[Audience]) orElse
      JsonDecoder[Set[Audience]].mapOrFail { audienceSet =>
        NonEmptySet
          .fromSet(SortedSet.from(audienceSet)(using Audience.order.toOrdering))
          .toRight("aud cannot be empty")
      }

}
