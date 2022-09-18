# TODO

- [x] TokenVerifier tests
- [x] Discovery tests
- [x] `default`, `tldr`, `bundle` or whatever module which can be quickly used
- [x] Caches
    - [x] Discovery
    - [x] PublicKeyProvider
- [x] Cache implementation tests?
- [x] Tests with Future
- [x] Location validation/sanitisation
- [x] separate types for some domain objects
- [x] TODOs
- [ ] API review
- [x] Packages review
- [ ] Docs
- [x] CI/CD

## TODO

- [ ] `implicits.impure` package with implementations for Try/Either/Future
- [ ] Scalafix

# oidc4s — purely functional Scala library for Open ID Connect 1.0 token verification
[![License](http://img.shields.io/:license-MIT-green.svg)](https://opensource.org/licenses/MIT)
[![Maven Central](https://img.shields.io/maven-central/v/me.wojnowski/oidc4s-core_3.svg?color=blue)](https://search.maven.org/search?q=oidc4s)

## Getting started
To use this library add the following dependency to your `build.sbt`:

```scala
"me.wojnowski" %% "oidc4s-quick-sttp-circe" % "x.y.z"
```

### Creating `IdTokenVerifier` instance

The next step depends on the runtime.

#### Cats Effect
```scala
import me.wojnowski.oidc4s.IdTokenVerifier
import me.wojnowski.oidc4s.config.Location

for {
  location        <- Sync[F].fromEither(Location.create("https://accounts.google.com"))
  backend         <- AsyncHttpClientCatsBackend[F]() // from async-http-client-backend-cats
  idTokenVerifier <- SttpCirceIdTokenVerifier.cachedWithCatsRef(location)(backend)
} yield idTokenVerifier
```

#### ZIO
TODO

#### Id/Try/Either
TODO

### Verifying a token
A token obtained on server side (e.g. using OAuth 2.0 Authorization Code flow) or front-end side
(e.g. using OAuth 2.0 Authorization Code flow with [PKCE](https://oauth.net/2/pkce/)) can now be verified:
```scala
import me.wojnowski.oidc4s.IdTokenVerifier
import me.wojnowski.oidc4s.ClientId
import me.wojnowski.oidc4s.IdTokenClaims.Subject

val verifier: IdTokenVerifier[F] = ...

val clientId = ClientId(...)

val rawToken: String = ...

val result: F[Either[IdTokenVerifier.Error, Subject]] = verifier.verify(rawToken, clientId)
```
## Token verification
There are a few verification methods. Depending on level of details, you can choose only to
verify a token is valid and matches the client ID. The method returns `Subject` (usually some kind of user ID):

```scala
def verify(rawToken: String, expectedClientId: ClientId): F[Either[IdTokenVerifier.Error, IdTokenClaims.Subject]]
```

If a little bit of more information is needed, a version with all standard ID token claims can be used
(client ID must be verified manually):
```scala
def verifyAndDecode(rawToken: String): F[Either[IdTokenVerifier.Error, IdTokenClaims]]
```

or with full result, which also contains header and claims JSONs (e.g. for decoding non-standard ID token fields).
```
def verifyAndDecodeFullResult(rawToken: String): F[Either[IdTokenVerifier.Error, IdTokenVerifier.Result]]
```

Full Result example:
```
Right(
  IdTokenVerifier.Result(
    rawHeaderJson = """{"alg":"RS256","kid":"f9d97b4cae90bcd76aeb20026f6b770cac221783","typ":"JWT"}""",
    rawClaimsJson = """{"aud":"https://example.com/path","azp":"integration-tests@chingor-test.iam.gserviceaccount.com","email":"integration-tests@chingor-test.iam.gserviceaccount.com","email_verified":true,"exp":1587629888,"iat":1587626288,"iss":"https://accounts.google.com","sub":"104029292853099978293"}"""
    IdTokenClaims(
      issuer = Issuer(https://accounts.google.com),
      subject = Subject(112741830942876971457),
      audience = Set(Audience(407408718192.apps.googleusercontent.com)),
      expiration = 2022-09-18T12:40:09Z, // java.time.Instant
      issuedAt = 2022-09-18T11:40:09Z, // java.time.Instant
      authorizedParty = Some(AuthorizedParty(407408718192.apps.googleusercontent.com))
    )
  )
)
```

## Modules and dependencies
The library has been modularised not to introduce too many dependencies, especially in terms
of JSON decoding and HTTP layer. These are implemented in their own modules, and can be swapped out
if needed.

Thus, `oidc4s-core` module defines `Transport` and `JsonSupport` abstractions. Currently, `JsonSupport`
is implemented with [Circe](https://github.com/circe/circe) and `Transport` with [sttp](https://github.com/softwaremill/sttp),
which can be heavily customised on its own.

There are plans to add integrations with ZIO (facades and layers for ease of use) and `zio-json`
as `JsonSupport`.

Currently available modules:
```
"me.wojnowski" %% "oidc4s-core" % "x.y.z"
"me.wojnowski" %% "oidc4s-circe" % "x.y.z"
"me.wojnowski" %% "oidc4s-sttp" % "x.y.z"
```

There's also an aggregation layer exposing handy constructors:
```
"me.wojnowski" %% "oidc4s-quick-circe-sttp" % "x.y.z"
```