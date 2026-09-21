import SwiftUI
import UserNotifications
import ComposeApp
import GoogleSignIn

/// The APNs device token and foreground presentation of pushes. Kotlin asks
/// for permission and registration (IosPush.kt); the token can only arrive here.
class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        IosPushKt.setRemoteRegistration { UIApplication.shared.registerForRemoteNotifications() }
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        IosPushKt.offerDeviceToken(hex: deviceToken.map { String(format: "%02x", $0) }.joined())
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        IosPushKt.offerDeviceToken(hex: nil)
    }

    // Show a push that arrives while the app is open, as the system would in the background.
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .sound, .badge])
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                // Compose paints the whole screen, safe areas included.
                //
                // Without this the status bar and the home indicator show the
                // window's own background, which follows the system. The app's
                // dark mode is its own switch in Settings, so a person reading
                // in the dark got two white slabs above and below a dark app.
                // Compose already insets its content, so nothing lands under
                // the notch — only the colour changes.
                //
                // .ignoresSafeArea() covers the keyboard region too, which is
                // what the narrower call here used to do on its own.
                .ignoresSafeArea()
                // poster://join/CODE — handed straight to shared code, which
                // decides whether it is an invite and holds it until the app is
                // in a position to act on it.
                .onOpenURL { url in
                    // Google's sign-in redirect (the reversed client id scheme)
                    // comes back here; let the SDK take it first. Anything else
                    // is an invite link.
                    if GIDSignIn.sharedInstance.handle(url) { return }
                    IosInviteLinkKt.offerInviteUrl(url: url.absoluteString)
                }
        }
    }
}
