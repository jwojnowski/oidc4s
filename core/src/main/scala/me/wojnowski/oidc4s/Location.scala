package me.wojnowski.oidc4s

import cats.syntax.all._
import me.wojnowski.oidc4s.Location.Error.InvalidLocation

abstract sealed case class Location(url: String)

object Location {

  // TODO Think about using cats-parse or refined
  /**
   * @param raw Base address of Open ID provider, e.g. "https://accounts.google.com", "https://appleid.apple.com" or "https://yourdomain.eu.auth0.com"
    */
  def create(raw: String): Either[Error.InvalidLocation, Location] =
    for {
      _ <- Either.cond(raw.startsWith("https://"), (), InvalidLocation("Location must begin with https://"))
      _ <- Either.cond(!raw.endsWith("/"), (), InvalidLocation("Location cannot end with /"))
      _ <- Either.cond(raw.trim === raw, (), InvalidLocation("Location cannot contain white characters"))
    } yield new Location(raw) {}

  def unsafeCreate(raw: String): Location = create(raw).fold(throw _, identity)

  sealed trait Error extends ProductSerializableNoStackTrace

  object Error {
    case class InvalidLocation(details: String) extends Error
  }

}
