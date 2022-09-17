package me.wojnowski.oidc4s

import scala.util.control.NoStackTrace

private[oidc4s] trait ProductSerializableNoStackTrace extends NoStackTrace with Product with Serializable {
  override def toString: String = productIterator.mkString(productPrefix + "(", ",", ")")
}
