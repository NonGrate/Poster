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

    func signIn(nonce: String, completion: @escaping (String?, String?) -> Void) {
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientId)

        // Always the interactive call, and always with the nonce.
        //
        // This used to restore a previous session silently and refresh its
        // id_token, which was nicer: no sheet at all on a second sign-in. A
        // refreshed token carries no nonce, though, and the server now refuses
        // a token it cannot tie to the sign-in that asked for it — so a silent
        // token would simply be rejected. For an account that has already
        // granted access the sheet is one tap and no consent screen, which is
        // the price of the token meaning something.
        interactiveSignIn(nonce: nonce, completion: completion)
    }

    private func interactiveSignIn(nonce: String, completion: @escaping (String?, String?) -> Void) {
        guard let presenter = Self.topViewController() else {
            completion(nil, "No window to present sign-in")
            return
        }
        GIDSignIn.sharedInstance.signIn(withPresenting: presenter, hint: nil, additionalScopes: nil, nonce: nonce) { result, error in
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
