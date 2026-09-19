# Store listings

What Play and the App Store will ask for, and where the template already has it.

## Pages the stores require

All served by the server, bilingual, no external assets:

| Requirement | URL |
|---|---|
| Privacy policy | `https://your.domain/privacy` |
| Terms | `https://your.domain/terms` |
| Account deletion reachable without the app (Play requires it) | `https://your.domain/delete-account` |
| Child safety standards (Play, social apps) | `https://your.domain/child-safety` |
| Home page for the OAuth consent screen | `https://your.domain/` |

Read them before publishing. They describe *this* code truthfully (no analytics,
no images, no messaging, what is stored and for how long). If you add any of
those things the pages become false. Each has an "Operator:" comment where a
value is yours: contact email (`POSTER_CONTACT_EMAIL`), hosting location, the
child-safety hotline for your country, "last updated".

## Google Play

1. **Developer account.** Personal accounts must run a closed test with ~12
   testers for ~14 days before production access; start that early.
2. **App integrity → Play App Signing**: on by default. Note the *app signing*
   certificate's SHA-1 (for the Google OAuth Android client) and SHA-256 (for
   `POSTER_ANDROID_FINGERPRINTS`). The upload key is a different key.
3. **Store listing**: name, short/full description, screenshots from
   `store/screenshots/<locale>/`, feature graphic from `store/graphics/`, icon
   from `build/icons/play-icon-512.png`.
4. **Data safety** form — answer from what the server stores
   (`docs/Database.md`): email, name, user-generated content; no advertising,
   no analytics, no location. Data is encrypted in transit; users can request
   deletion (in-app and web).
5. **Content rating**, **target audience** (not for children), **News/social**
   category → child safety standards URL above.
6. Upload the `.aab` from `./gradlew :composeApp:bundleRemoteRelease` and the
   R8 mapping file.

## App Store

1. **Apple Developer Program** ($99/yr). Nothing — no App Store Connect record,
   no TestFlight, no signed device build — exists before it is paid.
2. **App ID** with *Sign in with Apple* if you offer Google (Guideline 4.8
   requires it). [`SignIn.md`](SignIn.md).
3. **App Store Connect**: record, privacy policy URL, **App Privacy**
   questionnaire (same answers as Play's data safety), age rating.
4. Screenshots: `store/ios-screenshots/<locale>/` (6.7") and
   `store/ios-screenshots-ipad/` if you support iPad. App Store Connect also
   accepts the composed 1284×2778 images for the 6.9" slot.
5. `ITSAppUsesNonExemptEncryption` is already `false` in `Info.plist` (standard
   TLS only), so the export-compliance question is pre-answered.
6. Archive in Xcode with your `TEAM_ID`; distribute through TestFlight first.

## After launch

- Set `POSTER_PLAY_URL` / `POSTER_APPSTORE_URL` so the landing page links to the
  stores instead of saying "coming soon".
- Watch `/admin/crashes`, `/admin/reports` and `/admin/feedback`, or wire
  Telegram alerts so they come to you.
