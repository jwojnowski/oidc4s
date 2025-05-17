package me.wojnowski.oidc4s

import me.wojnowski.oidc4s.IdTokenClaims._

import cats.Eq
import cats.Order
import cats.data.NonEmptySet
import cats.syntax.all._

import java.time.Instant

case class IdTokenClaims(
  issuer: Issuer,
  subject: Subject,
  audience: NonEmptySet[Audience],
  expiration: Instant,
  issuedAt: Instant,
  authenticationTime: Option[Instant] = None,
  nonce: Option[Nonce] = None,
  authenticationContextClassReference: Option[AuthenticationContextClassReference] = None,
  authenticationMethodsReference: List[AuthenticationMethodReference] = List.empty,
  authorizedParty: Option[AuthorizedParty] = None
) {

  def matchesClientId(clientId: ClientId): Boolean =
    authorizedParty match {
      case Some(AuthorizedParty(value)) => value === clientId.value
      case None                         => audience.map(_.value).contains_(clientId.value)
    }

}

object IdTokenClaims {
  case class Subject(value: String) extends AnyVal

  object Subject {
    implicit val eq: Eq[Subject] = Eq.by(_.value)
  }

  case class Audience(value: String) extends AnyVal

  object Audience {
    implicit val eq: Eq[Audience] = Eq.by(_.value)
    implicit val order: Order[Audience] = Order.by(_.value)
  }

  case class Nonce(value: String) extends AnyVal

  object Nonce {
    implicit val eq: Eq[Nonce] = Eq.by(_.value)
  }

  case class AuthorizedParty(value: String) extends AnyVal

  object AuthorizedParty {
    implicit val eq: Eq[AuthorizedParty] = Eq.by(_.value)
  }

  case class AuthenticationContextClassReference(value: String) extends AnyVal

  object AuthenticationContextClassReference {
    implicit val eq: Eq[AuthenticationContextClassReference] = Eq.by(_.value)
  }

  case class AuthenticationMethodReference(value: String) extends AnyVal

  object AuthenticationMethodReference {
    implicit val eq: Eq[AuthenticationMethodReference] = Eq.by(_.value)
  }

}
