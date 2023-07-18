package me.wojnowski.oidc4s.json.circe

import cats.data.NonEmptySet
import io.circe.Decoder
import me.wojnowski.oidc4s.IdTokenClaims.Audience

import scala.collection.immutable.SortedSet

trait AudienceCirceDecoder {
  protected implicit val audienceDecoder: Decoder[Audience] =
    Decoder[String].map(Audience.apply)

  protected implicit val audienceNesDecoder: Decoder[NonEmptySet[Audience]] =
    Decoder[Audience].map(NonEmptySet.one[Audience]) or Decoder[Set[Audience]].emap { audienceSet =>
      NonEmptySet.fromSet(SortedSet.from(audienceSet)(Audience.order.toOrdering)).toRight("aud cannot be empty")
    }

}
