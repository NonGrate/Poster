# Push notifications and the activity list

`feature.pushNotifications` (on by default). Two things under one flag:

- **Activity list** — a screen (Settings → Activity, and the bell on Home with
  a dot when something is unread) of what happened to you: somebody liked or
  commented on your post, you were added to a group, somebody joined a group
  you own. Stored on the server (`Notification` table), so it is the same on
  every device. Opening the list marks it read.
- **Pushes** — the same events delivered to the device: FCM on Android, APNs
  on iOS. Without keys the server keeps the list and logs what it would have
  pushed, so the feature works in development with nothing configured.

Nobody is told about their own actions.

## Set-up

### Server

| Variable | Purpose |
|---|---|
| `POSTER_FCM_PROJECT_ID` | Firebase project id. |
| `POSTER_FCM_SERVICE_ACCOUNT` | Path to a service-account JSON (Firebase console → Project settings → Service accounts → *Generate new private key*). Only `client_email` and `private_key` are read. Mount it as a secret file; never commit it. |
| `POSTER_APNS_KEY_PATH` | Path to the `.p8` key (Apple Developer → Keys → *Apple Push Notifications service*). |
| `POSTER_APNS_KEY_ID`, `POSTER_APNS_TEAM_ID` | From the same key page and your team. |
| `POSTER_APNS_TOPIC` | The app's bundle id. |
| `POSTER_APNS_SANDBOX` | `true` for development builds (Xcode-installed apps talk to the sandbox), unset for App Store / TestFlight. |

A platform whose variables are missing gets `LoggingPushSender`; the startup
log says so. Dead tokens (unregistered, uninstalled) are dropped when the
platform reports them.

### Android

Four public values from Firebase console → Project settings → Your apps →
the Android app, in `gradle.properties`:

```properties
posterFirebaseProjectId=my-project
posterFirebaseAppId=1:1234567890:android:abcdef
posterFirebaseApiKey=AIza...
posterFirebaseSenderId=1234567890
```

No `google-services.json` and no Gradle plugin: `PosterApplication` builds
`FirebaseOptions` from these. All blank = Firebase is never initialised. The
Firebase Messaging dependency is only on the classpath with the feature on
(`composeApp/src/push/{enabled,disabled}` — the same pattern as billing). The
API key here is the Android app's *public* Firebase key; restrict it in Google
Cloud to the Firebase Installations / FCM APIs and the app's certificate.

### iOS

In Xcode: target → Signing & Capabilities → **+ Push Notifications** (this
adds `aps-environment` to the entitlements; the App ID needs the capability
too). Nothing else: the token flows Swift → Kotlin (`AppDelegate` in
`iOSApp.swift` → `offerDeviceToken`). The simulator gets no APNs token;
test on a device.

## How it works

```
sign-in ─▶ PushRegistrar: requestPushToken() ─▶ POST /devices {token, platform}
sign-out ─▶ DELETE /devices/{token}   (no session needed; the token is the proof)
like / comment / group add ─▶ Notifier: Notification row + PushSender per device
app: GET /notifications, GET /notifications/unread, POST /notifications/read
```

| Where | What |
|---|---|
| `shared/.../db/Device.sq`, `Notification.sq`, `3.sqm` | Tables (schema version 4). |
| `shared/.../model/AppNotification.kt` | Wire model, `NotificationType`. |
| `shared/.../repository/NotificationRepository.kt` | List, read, register, withdraw; remembers the registered token in preferences. |
| `composeApp/.../notification/PushTokens.kt` (+ `push/{enabled,disabled}`, `IosPush.kt`) | `requestPushToken()` per platform; `PushTokenRefresh` for rotations. |
| `composeApp/.../notification/PushRegistrar.kt` | Follows the session: register / re-register / withdraw. |
| `composeApp/.../ui/screens/NotificationsScreen.kt`, `viewmodel/NotificationsViewModel.kt` | The list and the unread count. |
| `server/.../push/PushSender.kt` | `FcmSender` (HTTP v1, service-account OAuth), `ApnsSender` (token auth, ES256, HTTP/2 via the JDK client), `LoggingPushSender`. |
| `server/.../push/Notifier.kt` | Events → rows + pushes, copy in the recipient's language. |
| `server/.../push/NotificationRoutes.kt` | `/notifications*`, `/devices`. |

Hooks live where the events happen: `POST /favorites/{user}/{post}` (like),
`POST /posts/{id}/comments` (comment), `POST /groups/join` (owner hears of a
join), `/admin/groups/{id}/add` (member hears of being added).

## Behaviour to know

- Permission: iOS asks when the token is first requested (after sign-in).
  Android 13+ asks once after the first sign-in (`AppPreferences.pushPrompted`);
  the token is registered regardless, the prompt only controls display.
- Tapping a push opens the app, not the post. The payload carries `postGuid`;
  routing it to Details is the obvious next step (`MainActivity.handleLink`
  and `AppDelegate` both already receive intents/URLs).
- The push text is composed on the server in the recipient's reading language
  (`Notifier.body`, en/ru) because the device cannot translate a push before
  showing it. Add languages there when you add them to the app.
- Rows are capped to the newest 100 in `GET /notifications`; nothing prunes the
  table (a thousand users with fifty events each is still tiny).
- The privacy page does not need a change: device tokens are the one new
  personal datum and they leave with the account (`ON DELETE CASCADE`).

## Tests

`server/src/test/.../NotificationRoutesTest.kt` (events → rows and pushes,
self-actions ignored, language, read state, dead and withdrawn tokens,
validation) and `PushSendersTest.kt` (FCM request shape and token reuse
against a mock engine; APNs ES256 provider token, headers and payload against a
fake transport). The instrumented suite does not cover pushes: the emulator
has no Firebase project and the simulator no APNs.
