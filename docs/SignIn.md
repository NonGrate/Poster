# Sign-in

Three ways in, all producing the same session (a JWT access token + a rotating
refresh token): **email + password**, **Google**, **Apple**. Email/password works
out of the box. Google and Apple stay switched off until you configure them —
the server answers `501` and the app shows no button — so a missing id is an
ordinary state, not a broken one.

## Email and password

Registration → verification email → (optionally) sign in with a group code.
Passwords are Argon2id-hashed; 8–128 characters. Access tokens last 15 minutes,
refresh tokens 30 days and rotate on each use; Android keeps them in an
Android-Keystore-encrypted store, iOS in the Keychain.

Writing a post requires a verified address (`feature.emailVerificationRequired`).
Verification and reset links go to web pages the server serves (`/verify`,
`/reset`) — a link in an email is opened wherever the email is read — and those
pages offer a `poster://` link into the app. Opening a link never spends the
token; pressing the button on the page does (mail scanners follow links).

Emails are printed to the server log until [`Email.md`](Email.md) is done.

## Google

You need three OAuth client ids in one Google Cloud project, plus a consent screen.

### 1. Consent screen

console.cloud.google.com → your project → **Google Auth Platform** (older
consoles: APIs & Services → OAuth consent screen):

- User type **External**; app name, support email, developer contact.
- Scopes: none beyond the defaults. The server reads `email`, `email_verified`,
  `sub`, `given_name`, `family_name`.
- While unpublished, add your testers under **Test users** or they are refused.
- Publishing later requires proving you own the home page / privacy URL. The
  server emits the Search Console HTML-tag token on every page when
  `POSTER_SITE_VERIFICATION` is set; a DNS TXT record also works.

### 2. Web client id — the one the code uses

**Credentials → Create → OAuth client ID → Web application.** No redirect URIs,
no JavaScript origins: the server verifies ID tokens itself.

Put the same value in two places, and they must match:

```properties
# gradle.properties (public; ships in the Android app)
posterGoogleClientId=1234567890-abc.apps.googleusercontent.com
```
```bash
# server
POSTER_GOOGLE_CLIENT_ID=1234567890-abc.apps.googleusercontent.com
```

Blank on the Android side hides the button; blank on the server disables the
verifier.

### 3. Android client id — one per package name and signing key

**Create → OAuth client ID → Android.** Package name = your `applicationId`
(and `<applicationId>.test` for the `e2e` flavour if you want the button there —
by default e2e ships without Google). SHA-1 of the signing certificate:

```bash
# debug key
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
# release / upload key
keytool -list -v -keystore ~/keys/upload.jks -alias upload
```

With Play App Signing, the store build is signed by *Google's* app signing key —
add its SHA-1 too (Play Console → Test and release → App integrity / "Play app
signing"). Google sign-in working in debug and failing in release is always this.

The Android client id is never referenced in code; Google matches it by
package + certificate.

### 4. iOS client id + the native SDK

**Create → OAuth client ID → iOS**, bundle id = yours. Then:

1. In Xcode: File → Add Package Dependencies → `https://github.com/google/GoogleSignIn-iOS`,
   product **GoogleSignIn** → target *iosApp*. (Already present in the template's
   `Package.resolved`; Xcode re-resolves it.)
2. `iosApp/Configuration/Config.xcconfig`:
   ```
   POSTER_GOOGLE_CLIENT_ID=<id>.apps.googleusercontent.com
   POSTER_GOOGLE_REVERSED_CLIENT_ID=com.googleusercontent.apps.<id>
   ```
   The reversed id is the URL scheme the SDK's redirect returns on; `Info.plist`
   registers it.
3. Add the iOS id to the server's list, comma-separated:
   `POSTER_GOOGLE_CLIENT_ID=<web-id>,<ios-id>`. `GoogleVerifier` accepts any id in
   the list, which is what lets an iOS-minted token verify on the same route.

The iOS side uses Google's native SDK (silent re-auth after the first grant),
bridged by `iosApp/iosApp/GoogleSignInBridge.swift` to the shared
`GoogleSignInLauncher`. Android uses Credential Manager (`AndroidGoogleCredentials.kt`).

### What the server does with the token

`server/.../auth/SocialSignIn.kt` checks signature (Google's published JWKs,
cached), issuer, audience (your ids), expiry, and **`email_verified`**. An
identity `(provider, subject)` is then linked to an account (`SocialIdentity`
table); the first time, it is matched by verified email so a Google sign-in
lands on the account somebody registered with a password rather than creating a
second one. `GoogleSignInTest` covers every rule with a locally generated key —
no network.

## Apple

Required by App Store Review Guideline 4.8 if you offer Google. Fully built on
both platforms; what is left is the Apple Developer portal.

### iOS — native

1. developer.apple.com → Identifiers → your **App ID** → enable **Sign in with Apple**.
2. Xcode → target iosApp → Signing & Capabilities → **+ Sign in with Apple**
   (the entitlement file is already in the project).
3. Server: `POSTER_APPLE_BUNDLE_ID=<your bundle id>`.

Token audience is the bundle id. Bridged by `AppleSignInBridge.swift`.

### Android — a browser round-trip through the server

Apple has no Android SDK. The app opens Apple's web sign-in
(`AndroidAppleCredentials.kt`), Apple `form_post`s the identity token to
`https://<your domain>/auth/apple/callback`, the server bounces a **one-time
code** back to the app as a `poster://auth/apple?…` deep link, the app trades
that code for the token at `/auth/apple/exchange`, and then calls `/auth/social`
like any other provider. Needs a public https server (a tunnel in development —
`scripts/dev-tunnel.sh public`).

Why the extra step: a custom scheme is first-come on Android, so any installed
app may declare `poster://` and receive that redirect. An identity token in the
URL would therefore be a sign-in handed to whoever asked. The app generates a
random verifier, sends only its SHA-256 (appended to `state`, which Apple
echoes untouched), and redeems the code with the verifier over HTTPS — so an
app that intercepts the redirect holds a code that buys it nothing. This is the
proof-key exchange RFC 8252 asks of a native app; the pieces are
`AppleCodes.kt` on the server and `AndroidAppleCredentials.kt` in the app.

An app built before this existed sends a `state` with no challenge and the
callback answers with an error rather than the token it is waiting for. That
error text is written as a sentence on purpose: an older build puts whatever
arrives there straight into what it shows the person, and its wording cannot
be changed after the fact — only what it is handed. If you ship an update that includes this, Apple sign-in on Android stops
working for people still on the older build until they update.

1. Portal → Identifiers → **Services ID** (e.g. `com.acme.chirp.signin`) →
   enable Sign in with Apple → primary App ID = yours → domain = your host →
   return URL = `https://<host>/auth/apple/callback`.
2. Domain verification: Apple gives `apple-developer-domain-association.txt`;
   put its *contents* in `POSTER_APPLE_DOMAIN_ASSOCIATION` — the server serves it
   at `/.well-known/apple-developer-domain-association.txt`.
3. Server: `POSTER_APPLE_SERVICE_ID=<services id>`.
4. Android build: `posterAppleServiceId=<services id>` in `gradle.properties`
   (blank hides the button on Android).

No `.p8` key and no client secret: the flow uses the identity token Apple
returns directly (`response_type=code id_token`), verified by audience.

### Hidden email and account merging

Somebody choosing *Hide My Email* arrives with a `@privaterelay.appleid.com`
address that can never match an existing account. The app notices and offers
**Merge with your existing account** (right after sign-in and later in Settings):
they prove the other account with its password or with Google, and
`POST /auth/social/merge` moves everything (posts, likes, memberships,
identities) onto it. `AccountMergeTest` and `SocialIdentityLinkingTest` cover it.

## Turning a provider off

`feature.googleSignIn=false` / `feature.appleSignIn=false` in `poster.properties`
removes the button regardless of ids. Or simply leave the ids blank.

## Files

| | |
|---|---|
| Server verification | `server/.../auth/SocialSignIn.kt` (`GoogleVerifier`, `AppleVerifier`), `AuthRoutes.kt` (`/auth/social`, `/auth/apple/callback`, `/auth/social/merge`), `AuthService.signInWithProvider` |
| Android | `composeApp/src/androidMain/.../auth/AndroidGoogleCredentials.kt`, `AndroidAppleCredentials.kt`; ids from `BuildConfig` via `gradle.properties` |
| iOS | `iosApp/iosApp/GoogleSignInBridge.swift`, `AppleSignInBridge.swift`; `composeApp/src/iosMain/.../auth/Ios*Credentials.kt`; ids from `Config.xcconfig` → `Info.plist` |
| Shared | `composeApp/src/commonMain/.../auth/GoogleCredentials.kt`, `AppleCredentials.kt`; `ui/screens/LoginScreen.kt`; `ui/components/MergeAccountDialog.kt` |
| Tests | `server/src/test/.../GoogleSignInTest.kt`, `AppleSignInTest.kt`, `SocialSignInRoutesTest.kt`, `SocialIdentityLinkingTest.kt`, `AccountMergeTest.kt` |

## Passwordless sign-in (magic link)

`feature.magicLink` (on by default). The login screen offers *Sign in with an
emailed link*. The server emails a link good for fifteen minutes and one use;
following it on the device with the app signs in, and confirms the address if
it was not confirmed yet. Sign-in only: an address with no account gets no mail
(and the same 204), so people register first and then never need the password.

```
app  POST /auth/magic/request {email}  → always 204 (says nothing about who has an account)
mail https://your.domain/magic?token=… → page hands off to poster://magic?token=…
app  POST /auth/magic {token}          → AuthResponse (session), or 400 when spent/expired
```

Needs the mailer ([`Email.md`](Email.md)); with none configured the link is
printed in the server log, which is how you test it locally. Requests are
throttled like verification and reset mails (`AccountMail.allowed`). The
landing page never spends the token itself, so mail clients that prefetch links
do not burn the sign-in. Code: `AuthService.signInWithMagicLink`,
`accountForMagicLink` (lookup only), `AccountMail.sendMagicLink`, `AppLink.Magic`,
`MagicDialog` in `AppLinkHandler.kt`, `MagicLinkDialog` in `LoginScreen.kt`.
Test: `MagicLinkRoutesTest`.

