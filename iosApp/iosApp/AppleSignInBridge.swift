import Foundation
import UIKit
import AuthenticationServices
import ComposeApp

/// Sign in with Apple through the native ASAuthorizationController, bridged to
/// the Kotlin `AppleSignInLauncher` the shared code expects.
///
/// The iOS counterpart of `GoogleSignInBridge`: it hands the shared code an
/// Apple identity token, and the server decides whether it is genuine (audience
/// = the app's bundle id). Only the identity token is used — the one-time name
/// and email Apple returns on first sign-in are ignored here, because the server
/// derives the account from the verified token.
final class AppleSignInBridge: NSObject, AppleSignInLauncher {
    // Held while a request is in flight; ASAuthorizationController keeps only
    // weak references to its delegate and presentation provider. This bridge is
    // a long-lived singleton, so `self` stays alive; the controller is retained
    // here so it is not deallocated mid-flow.
    private var completion: ((String?, String?) -> Void)?
    private var controller: ASAuthorizationController?

    func signIn(nonce: String, completion: @escaping (String?, String?) -> Void) {
        self.completion = completion
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        // The hash of a value the shared code generated. Apple puts it in the
        // identity token, and the server checks that whoever presents the
        // token can also produce the value behind it — so a token on its own
        // is no longer enough to sign in as its subject.
        request.nonce = nonce
        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self
        self.controller = controller
        controller.performRequests()
    }

    private func finish(_ token: String?, _ error: String?) {
        let callback = completion
        completion = nil
        controller = nil
        callback?(token, error)
    }
}

extension AppleSignInBridge: ASAuthorizationControllerDelegate {
    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization authorization: ASAuthorization
    ) {
        guard
            let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
            let tokenData = credential.identityToken,
            let token = String(data: tokenData, encoding: .utf8)
        else {
            finish(nil, "Apple returned no identity token")
            return
        }
        finish(token, nil)
    }

    func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError error: Error
    ) {
        // A cancel is not a failure to report — null token, no message.
        if let authError = error as? ASAuthorizationError, authError.code == .canceled {
            finish(nil, nil)
        } else {
            finish(nil, error.localizedDescription)
        }
    }
}

extension AppleSignInBridge: ASAuthorizationControllerPresentationContextProviding {
    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        let scene = UIApplication.shared.connectedScenes
            .first { $0.activationState == .foregroundActive } as? UIWindowScene
        return scene?.keyWindow
            ?? UIApplication.shared.windows.first
            ?? ASPresentationAnchor()
    }
}
