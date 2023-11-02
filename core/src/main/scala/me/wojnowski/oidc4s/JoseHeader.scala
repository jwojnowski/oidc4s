package me.wojnowski.oidc4s

/** Represents JOSE (JSON Object Signing and Encryption) header https://datatracker.ietf.org/doc/html/rfc7515#section-4 */
case class JoseHeader(keyId: String, algorithm: Algorithm)
