package me.wojnowski.oidc4s

import cats.Order
import cats.data.NonEmptySet
import cats.implicits._

sealed abstract class Algorithm(val name: String, val fullName: String) extends Product with Serializable

// According to OIDC RFC, only RS256 should be supported
object Algorithm {
  case object Rs256 extends Algorithm(name = "RS256", fullName = "SHA256withRSA")
  case object Rs384 extends Algorithm(name = "RS384", fullName = "SHA384withRSA")
  case object Rs512 extends Algorithm(name = "RS512", fullName = "SHA512withRSA")

  case class Other(override val name: String) extends Algorithm(name, fullName = name)

  implicit val order: Order[Algorithm] = Order.by(_.name)

  val supportedAlgorithms: NonEmptySet[Algorithm] = NonEmptySet.of(Rs256, Rs384, Rs512)

  def fromString(s: String): Algorithm = supportedAlgorithms.find(_.name === s).getOrElse(Other(s))
}
