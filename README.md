# oidc4s — Open ID Connect 1.0 token verification for Scala 

[![License](http://img.shields.io/:license-MIT-green.svg)](https://opensource.org/licenses/MIT)
[![Maven Central](https://img.shields.io/maven-central/v/me.wojnowski/oidc4s-core_3.svg?color=blue)](https://search.maven.org/search?q=oidc4s)

This library can be used to verify and decode ID Tokens from Open ID Connect 1.0 providers like Google, Microsoft,
Apple, Auth0, Okta.

Combined with [OAuth 2.0 Authorization Code](https://oauth.net/2/grant-types/authorization-code/) flow with [PKCE](https://oauth.net/2/pkce/)
on front-end side can lead to a simple, yet secure authentication for Single Page Applications.

Scala versions 3.x and 2.13.x are supported.

[JWT Scala](https://github.com/jwt-scala/jwt-scala) is used for JWT verification under-the-hood. 

## Getting started

To use this library with default Sttp/Circe implementations, add the following dependency to your `build.sbt`:

```scala
"me.wojnowski" %% "oidc4s-quick-sttp-circe" % "x.y.z"
```

### Creating `IdTokenVerifier` instance

The next step depends on the runtime.

#### Cats Effect

For any effect with an existing implementation of `Sync` (e.g. Cats IO, ZIO, monix Task/IO),
a version with `cats.effect.Ref`-based Cache can be used, for example:

```scala
import me.wojnowski.oidc4s.IdTokenVerifier
import me.wojnowski.oidc4s.config.Location

for {
  location        <- Sync[F].fromEither(Location.create("https://accounts.google.com"))
  backend         <- AsyncHttpClientCatsBackend[F]() // from async-http-client-backend-cats
  idTokenVerifier <- SttpCirceIdTokenVerifier.cachedWithCatsRef(location)(backend)
} yield idTokenVerifier
```

#### Id/Try/Either

For entirely synchronous, impure usage, a combination of `import me.wojnowski.oidc4s.impure.implicits._` import
and `AtomicReference`-based Cache instance can be used, for example:

```scala
import me.wojnowski.oidc4s.IdTokenVerifier
import me.wojnowski.oidc4s.config.Location
import me.wojnowski.oidc4s.impure.implicits._
import sttp.client3.HttpClientSyncBackend

val backend: SttpBackend[Identity, Any] = HttpClientSyncBackend()

val verifier = SttpCirceIdTokenVerifier.cachedWithAtomicRef[Id](Location.unsafeCreate("https://accounts.google.com"))(backend)
```

While the example shows `Id` implementation, `Try` and `Either` would be very similar, but different
`SttpBackend` is needed.

### Verifying a token

```scala
import me.wojnowski.oidc4s.IdTokenVerifier
import me.wojnowski.oidc4s.ClientId
import me.wojnowski.oidc4s.IdTokenClaims.Subject

val verifier: IdTokenVerifier[F] = ...

val clientId = ClientId("<client-id>")

val rawToken: String = "eyJhdWQ..."

val result: F[Either[IdTokenVerifier.Error, Subject]] = verifier.verify(rawToken, clientId)
```

## Token verification and decoding

There are a few verification methods. Choosing one mostly comes down to which claims need to be decoded.

### Subject only (`verify`)
To just verify a token is valid and matches the client ID use `verify` method. It returns `Subject` (usually some kind of user ID):

```scala
def verify(rawToken: String, expectedClientId: ClientId): F[Either[IdTokenVerifier.Error, IdTokenClaims.Subject]]
```

### Standard claims (`verifyAndDecode`)
When more information is needed, a version with standard ID token claims can be used.

```scala
def verifyAndDecode(rawToken: String): F[Either[IdTokenVerifier.Error, IdTokenClaims]]
```

### Custom claims (`verifyAndDecodeCustom[A]`)
For full flexibility, `verifyAndDecodeCustom` method can be used. It requires `ClaimsDecoder[A]` instance, which
can be derived from JSON-library-specific decoder (see below for Circe example). 

```scala
def verifyAndDecodeCustom[A](rawToken: String)(implicit decoder: ClaimsDecoder[A]): F[Either[IdTokenVerifier.Error, A]]
```
#### Circe Example
```scala
case class CustomData(email: String, isAdmin: Boolean)
implicit val decoder: io.circe.Decoder[CustomData] = io.circe.generic.semiauto.deriveDecoder

import CirceJsonSupport._ // brings ClaimsDecoder[A: Decoder] instance into scope

val verifier: IdTokenVerifier[F] = ...
val rawToken: String = ...

val result: F[Either[IdTokenVerifier.Error, CustomData]] = verifier.verifyAndDecodeCustom[CustomData](rawToken)

```


## Configuration Discovery (and caching)

Open ID Connect provider configuration (issuer and JWKS URL) is read from Open ID Configuration Document
`<location>/.well-known/openid-configuration` (
e.g. `https://login.microsoftonline.com/common/.well-known/openid-configuration`)

If `Cache` is used, the configuration is cached according to HTTP cache headers with fallback
to the (configurable) default of 1 day.

## Public Key retrieval (and caching)

Signing keys are retrieved based on JWKS URL from the config. If `Cache` is used, they are cached indefinitely.
However, if a new key is encountered, all keys are replaced with newly read keys. This ensures both the
retrieval of new keys, and eventual removal of no longer used keys.

## Modules and dependencies

The library has been modularised not to introduce too many dependencies, especially in terms
of JSON decoding and HTTP layer. These are implemented in their own modules, and can be swapped out
if needed.

Thus, `oidc4s-core` module defines `Transport` and `JsonSupport` abstractions. Currently, `JsonSupport`
is implemented with [Circe](https://github.com/circe/circe) and `Transport`
with [sttp](https://github.com/softwaremill/sttp), which can be heavily customised on its own.

There are plans to add integrations with ZIO (facades and layers for ease of use) and `zio-json`
as `JsonSupport`.

Currently available modules:

```scala
"me.wojnowski" %% "oidc4s-core" % "x.y.z"
"me.wojnowski" %% "oidc4s-circe" % "x.y.z"
"me.wojnowski" %% "oidc4s-sttp" % "x.y.z"
```

There's also an aggregation layer exposing handy constructors:

```scala
"me.wojnowski" %% "oidc4s-quick-circe-sttp" % "x.y.z"
```

## Versioning and binary compatibility
For versions 0.x.y binary compatibility might be broken.

Version history starts from 0.5.0, as the core of this lib was previously a part of [googlecloud4s](https://github.com/jwojnowski/googlecloud4s).
