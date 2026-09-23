import UIKit
import SwiftUI
import ComposeApp

/// Reads the server and sign-in configuration from Info.plist once.
enum AppSetup {
    static let serverScheme = Bundle.main.object(forInfoDictionaryKey: "PosterServerScheme") as! String
    static let serverHost = Bundle.main.object(forInfoDictionaryKey: "PosterServerHost") as! String
    static let serverPort = Bundle.main.object(forInfoDictionaryKey: "PosterServerPort") as! String
    static let revenueCatApiKey = Bundle.main.object(forInfoDictionaryKey: "PosterRevenueCatKey") as? String ?? ""
    static let googleClientId = Bundle.main.object(forInfoDictionaryKey: "PosterGoogleClientId") as? String ?? ""
    static let googleSignIn: GoogleSignInLauncher? = googleClientId.isEmpty ? nil : GoogleSignInBridge(clientId: googleClientId)
    static let appleSignIn: AppleSignInLauncher? = AppleSignInBridge()
    static let demoPaywall = ProcessInfo.processInfo.environment["POSTER_DEMO_PAYWALL"] == "1"

    /// Starts the Kotlin dependency graph; safe to call more than once.
    static func prepare() {
        MainViewControllerKt.prepareApp(
            serverHost: serverHost, serverPort: Int32(serverPort)!, serverScheme: serverScheme,
            revenueCatApiKey: revenueCatApiKey, googleSignIn: googleSignIn, appleSignIn: appleSignIn, demoPaywall: demoPaywall
        )
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
#if DEBUG
        if ProcessInfo.processInfo.environment["POSTER_UI_TEST"] == "1" {
            // The keys come from the shared module so this cannot fall behind
            // it. Listing them here left the cached profile in place when one
            // was added, and the app restored a session while the test waited
            // for a login screen.
            for key in IosInviteLinkKt.sessionPreferenceKeys() {
                UserDefaults.standard.removeObject(forKey: key)
            }
        }
#endif
        let serverScheme = Bundle.main.object(forInfoDictionaryKey: "PosterServerScheme") as! String
        let serverHost = Bundle.main.object(forInfoDictionaryKey: "PosterServerHost") as! String
        let serverPort = Bundle.main.object(forInfoDictionaryKey: "PosterServerPort") as! String
        // Kotlin's default arguments do not survive the trip into Swift, so
        // every parameter has to be passed here — which is why adding one to
        // the shared module broke this file and nothing said so until an iOS
        // build was attempted.
        let revenueCatApiKey = Bundle.main.object(forInfoDictionaryKey: "PosterRevenueCatKey") as? String ?? ""
        // The iOS OAuth client id for Google sign-in. Blank means no button, the
        // same as a blank RevenueCat key hides support — then no bridge is made
        // and the shared code sees no launcher.
        let googleClientId = Bundle.main.object(forInfoDictionaryKey: "PosterGoogleClientId") as? String ?? ""
        let googleSignIn: GoogleSignInLauncher? =
            googleClientId.isEmpty ? nil : GoogleSignInBridge(clientId: googleClientId)
        // Sign in with Apple is native to iOS and needs no client id — the token
        // audience is the bundle id. Always offered; the server accepts it once
        // POSTER_APPLE_BUNDLE_ID is set.
        let appleSignIn: AppleSignInLauncher? = AppleSignInBridge()
        // Screenshots only: the capture test sets this so the paywall shows
        // placeholder tiers before a store account exists. Off in normal runs.
        let demoPaywall = ProcessInfo.processInfo.environment["POSTER_DEMO_PAYWALL"] == "1"
        return MainViewControllerKt.MainViewController(
            serverHost: serverHost,
            serverPort: Int32(serverPort)!,
            serverScheme: serverScheme,
            revenueCatApiKey: revenueCatApiKey,
            googleSignIn: googleSignIn,
            appleSignIn: appleSignIn,
            demoPaywall: demoPaywall
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// One tab of the native bar: a Compose view controller pinned to a route.
struct TabComposeView: UIViewControllerRepresentable {
    let route: String
    func makeUIViewController(context: Context) -> UIViewController {
        IosTabsKt.TabViewController(route: route)
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// Whether somebody is signed in, from Kotlin's session.
final class SessionState: ObservableObject {
    @Published var signedIn = false
    init() {
        AppSetup.prepare()
        IosTabsKt.observeSignedIn { [weak self] value in self?.signedIn = value.boolValue }
    }
}

struct ContentView: View {
    @StateObject private var session = SessionState()
    @State private var selection = "main"

    var body: some View {
        // feature.liquidNavBar on iOS: the system tab bar (Liquid Glass on iOS 26)
        // with one Compose view per tab. Signed out, or with the flag off, the
        // single Compose view draws everything itself.
        if IosTabsKt.nativeTabs() && session.signedIn {
            TabView(selection: $selection) {
                ForEach(IosTabsKt.tabRoutes(), id: \.self) { route in
                    TabComposeView(route: route)
                        // Full-bleed so the feed scrolls behind the glass bar;
                        // Compose adds LocalBottomBarInset so content clears it.
                        .ignoresSafeArea()
                        .tabItem {
                            Label(IosTabsKt.tabTitle(route: route), systemImage: IosTabsKt.tabSymbol(route: route))
                                .accessibilityIdentifier(IosTabsKt.tabTestTag(route: route))
                        }
                        .tag(route)
                }
            }
            // The app's primary, from the generated AccentColor asset, rather than
            // the system blue the bar would otherwise pick.
            .tint(Color("AccentColor"))
            .onAppear { IosTabsKt.setNativeTabSwitcher { route in selection = route } }
        } else {
            ComposeView()
                .ignoresSafeArea() // Compose handles the keyboard and its own insets
        }
    }
}
