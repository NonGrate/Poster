import Foundation
import UIKit
import GoogleSignIn
import ComposeApp

/// Google sign-in through Google's native iOS SDK, bridged to the Kotlin
/// `GoogleSignInLauncher` the shared code expects.
///
/// This is the iOS counterpart of Android's Credential Manager: the SDK keeps
/// its own session, so after the first grant re-auth is silent — no repeated
/// consent screen and no "access granted" email, which the earlier browser flow
/// could not avoid.
final class GoogleSignInBridge: NSObject, GoogleSignInLauncher {
    private let clientId: String

    init(clientId: String) {
        self.clientId = clientId
    }

    func signIn(completion: @escaping (String?, String?) -> Void) {
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientId)

        // The point of the native SDK: if this account was signed in before,
        // restore it silently — no browser, no consent screen, no "access
        // granted" email — and just refresh the id_token. Only when there is no
        // stored session (first sign-in, or after a revoke) fall back to the
        // interactive flow. We deliberately never call GIDSignIn.signOut on app
        // logout, so this session survives and re-login stays silent.
        if GIDSignIn.sharedInstance.hasPreviousSignIn() {
            GIDSignIn.sharedInstance.restorePreviousSignIn { [weak self] user, error in
                guard let user = user, error == nil else {
                    self?.interactiveSignIn(completion: completion)
                    return
                }
                user.refreshTokensIfNeeded { user, _ in
                    if let idToken = user?.idToken?.tokenString {
                        completion(idToken, nil)
                    } else {
                        self?.interactiveSignIn(completion: completion)
                    }
                }
            }
            return
        }

        interactiveSignIn(completion: completion)
    }

    private func interactiveSignIn(completion: @escaping (String?, String?) -> Void) {
        guard let presenter = Self.topViewController() else {
            completion(nil, "No window to present sign-in")
            return
        }
        GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, error in
            if let error = error as NSError? {
                // A cancel is not a failure to report — null token, no message.
                if error.code == GIDSignInError.canceled.rawValue {
                    completion(nil, nil)
                } else {
                    completion(nil, error.localizedDescription)
                }
                return
            }
            guard let idToken = result?.user.idToken?.tokenString else {
                completion(nil, "Google returned no id_token")
                return
            }
            completion(idToken, nil)
        }
    }

    /// The frontmost view controller to present the sign-in sheet from.
    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .first { $0.activationState == .foregroundActive } as? UIWindowScene
        var top = scene?.keyWindow?.rootViewController
            ?? UIApplication.shared.windows.first?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}
