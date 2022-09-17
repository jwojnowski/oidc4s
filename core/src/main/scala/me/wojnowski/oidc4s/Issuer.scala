package me.wojnowski.oidc4s

import cats.Eq

case class Issuer(value: String) extends AnyVal

object Issuer {
  implicit val eq: Eq[Issuer] = Eq.by(_.value)
}
