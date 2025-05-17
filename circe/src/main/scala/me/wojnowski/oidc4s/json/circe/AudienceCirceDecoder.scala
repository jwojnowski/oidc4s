package me.wojnowski.oidc4s.json.circe

import me.wojnowski.oidc4s.IdTokenClaims.Audience

import cats.data.NonEmptySet

import scala.collection.immutable.SortedSet

import io.circe.Decoder

trait AudienceCirceDecoder {
  protected implicit val audienceDecoder: Decoder[Audience] =
    Decoder[String].map(Audience.apply)

  protected implicit val audienceNesDecoder: Decoder[NonEmptySet[Audience]] =
    Decoder[Audience].map(NonEmptySet.one[Audience]) or Decoder[Set[Audience]].emap { audienceSet =>
      NonEmptySet.fromSet(SortedSet.from(audienceSet)(using Audience.order.toOrdering)).toRight("aud cannot be empty")
    }

}
