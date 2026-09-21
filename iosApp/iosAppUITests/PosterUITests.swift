import XCTest

/// Driving the app, shared by both suites.
///
/// These helpers used to exist twice, and the second copy typed without
/// checking that anything arrived — so the screenshot run could log in with an
/// empty password field and fail somewhere else entirely.
class PosterTestCase: XCTestCase {
    var app: XCUIApplication!

    func launchApp(extraEnvironment: [String: String] = [:], extraArguments: [String] = []) {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchEnvironment["POSTER_UI_TEST"] = "1"
        for (key, value) in extraEnvironment { app.launchEnvironment[key] = value }
        app.launchArguments += extraArguments
        app.launch()
    }

    override func tearDownWithError() throws {
        app?.terminate()
    }

    func element(_ identifier: String) -> XCUIElement {
        let match = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
        // With feature.liquidNavBar the tabs are SwiftUI's, and the system tab bar
        // does not always surface the identifier set on the label; the title does.
        if !match.exists, let title = Self.tabTitles[identifier] {
            let button = app.tabBars.buttons[title]
            if button.exists { return button }
        }
        return match
    }

    private static let tabTitles = [
        "feed_tab": "Feed", "my_posts_tab": "My Posts", "favourites_tab": "Liked", "settings_tab": "Settings",
    ]

    func waitFor(
        _ identifier: String,
        timeout: TimeInterval = 10,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        XCTAssertTrue(
            element(identifier).waitForExistence(timeout: timeout),
            "Expected accessibility identifier \(identifier). UI tree:\n\(app.debugDescription)",
            file: file,
            line: line
        )
    }

    func tap(
        _ identifier: String,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let target = element(identifier)
        waitFor(identifier, file: file, line: line)
        bringIntoReach(target)
        XCTAssertTrue(target.isHittable, "Expected \(identifier) to be hittable", file: file, line: line)
        target.tap()
    }

    /// Types, and proves the text arrived.
    ///
    /// A tap landing while the keyboard is still animating — or before Compose
    /// has attached its text input, which a cold first launch makes likely —
    /// swallows everything typed after it and leaves the field empty. Left
    /// unchecked that surfaces much later as "the next screen never appeared",
    /// pointing nowhere near the cause.
    ///
    /// The check is that the field *changed*. An empty field can report a
    /// placeholder as its label, so "not empty" would happily accept keystrokes
    /// that never landed.
    func type(
        _ text: String,
        into identifier: String,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let field = element(identifier)
        waitFor(identifier, file: file, line: line)
        bringIntoReach(field)
        XCTAssertTrue(field.isHittable, "Expected \(identifier) to be hittable", file: file, line: line)
        guard !text.isEmpty else { return }

        let before = field.contents()
        for _ in 0..<3 {
            field.tap()
            field.typeText(text)
            if field.waitForContents(toChangeFrom: before, timeout: 2) { return }
        }
        XCTFail(
            "Typing into \(identifier) did not take — it still reads \"\(before)\"",
            file: file,
            line: line
        )
    }

    /// Scrolls something into reach before touching it.
    ///
    /// A form grows: adding the language pickers to registration pushed the
    /// group code field under the keyboard, and the iOS suite failed on a
    /// field that was there all along. Android never noticed, which is the
    /// shape of every iOS-only break — so this puts the keyboard away and
    /// scrolls whichever container is on screen, rather than naming one.
    func bringIntoReach(_ target: XCUIElement) {
        if target.isHittable { return }
        dismissKeyboard()

        let containers = ["registration_dialog", "post_form", "group_management_screen", "profile_screen", "settings_screen"]
        for name in containers where !target.isHittable {
            let container = element(name)
            guard container.exists else { continue }
            // The screen may be scrolled anywhere from a prior interaction (a
            // long Settings screen leaves it deep after opening the paywall), so
            // go up toward the top first, then back down, to find the target
            // wherever it sits — swiping only up misses anything now above.
            for _ in 0..<8 where !target.isHittable { container.swipeDown() }
            for _ in 0..<8 where !target.isHittable { container.swipeUp() }
        }
        // A swipe leaves the list coasting, and a tap that lands mid-coast is
        // read as part of the scroll and swallowed — which left the Support
        // button tapped but the paywall unopened. Let the momentum stop first.
        Thread.sleep(forTimeInterval: 0.6)
    }

    /// Only ever the return key. A looser predicate matched some other key and
    /// typed a character into the field it was meant to be leaving.
    func dismissKeyboard() {
        guard app.keyboards.count > 0 else { return }
        let returnKey = app.keyboards.buttons["return"]
        if returnKey.exists && returnKey.isHittable { returnKey.tap() }
    }

    /// The account the fixtures seed. The screenshot run used to sign in as a
    /// hand-made account on whichever backend the developer happened to have,
    /// so it failed for anybody else — and proved nothing when it passed.
    func login(
        email: String = "test@example.com",
        password: String = "password123"
    ) {
        type(email, into: "login_field")
        type(password, into: "password_field")
        tap("login_button")
        waitFor("feed_screen", timeout: 20)
    }

    /// Puts the backend into a known state. Without it a run reads whatever the
    /// last one left behind.
    func applyIntegrationFixtures() throws {
        let completed = expectation(description: "Integration fixtures applied")
        var request = URLRequest(url: URL(string: "http://127.0.0.1:8080/debug/fixtures/integration")!)
        request.httpMethod = "POST"

        var responseStatus: Int?
        var responseError: Error?
        URLSession.shared.dataTask(with: request) { _, response, error in
            responseStatus = (response as? HTTPURLResponse)?.statusCode
            responseError = error
            completed.fulfill()
        }.resume()

        wait(for: [completed], timeout: 10)
        if let responseError {
            throw responseError
        }
        XCTAssertEqual(responseStatus, 204, "Start ./scripts/run-local-backend.sh before running iOS UI tests")
    }
}

final class PosterUITests: PosterTestCase {

    override func setUpWithError() throws {
        try applyIntegrationFixtures()
        launchApp()
        waitFor("login_screen")
    }

    func testLoginAndLogoutAgainstLocalServer() {
        login()
        tap("settings_tab")
        waitFor("settings_screen")
        tap("logout_button")
        tap("confirm_sign_out_button")
        waitFor("login_screen")
    }

    func testRegistrationJoinsGroup() {
        register(
            name: "iOS",
            surname: "Member",
            email: "ios.member@example.com",
            groupCode: "GROUP_A_INVITE"
        )

        tap("settings_tab")
        waitFor("settings_screen")
        tap("manage_groups_button")
        waitFor("group_management_screen")
        XCTAssertTrue(element("leave_group_group-a").waitForExistence(timeout: 10))
    }

    func testCreatePostWithTagAndGroup() {
        login()
        createPost(title: "iOS integration post")
        XCTAssertTrue(app.staticTexts["iOS integration post"].waitForExistence(timeout: 10))
        // The card shows the tag's label, not its id — "Wellbeing", not "wellbeing".
        XCTAssertTrue(app.staticTexts["Wellbeing"].waitForExistence(timeout: 10))
    }

    func testFavouritePostAndUndoRemoval() {
        register(
            name: "Post",
            surname: "Author",
            email: "ios.author@example.com",
            groupCode: "GROUP_A_INVITE"
        )
        createPost(title: "Post visible in the feed")
        logout()

        login()
        XCTAssertTrue(app.staticTexts["Post visible in the feed"].waitForExistence(timeout: 10))
        tap("favorite_button")
        waitFor("favorite_button_filled")

        tap("favourites_tab")
        waitFor("favourites_screen")
        waitFor("favourite_post_card")
        tap("remove_favourite_button")
        waitFor("undo_button")
        tap("undo_button")
        waitFor("favourite_post_card")
    }

    func testJoinAndLeaveGroup() {
        login()
        tap("settings_tab")
        waitFor("settings_screen")
        tap("manage_groups_button")
        waitFor("group_management_screen")

        // Joining lives in the "+" sheet now.
        tap("add_group_button")
        type("BOOK_CLUB_INVITE", into: "group_code_input")
        tap("join_group_submit_button")
        // On success the sheet closes and the room shows in the list, with Leave
        // in its overflow rather than as a word on the row.
        waitFor("group_overflow_book-club", timeout: 10)
        dismissKeyboard()
        element("group_management_screen").swipeUp()

        tap("group_overflow_book-club")
        tap("leave_group_book-club")
        waitFor("confirm_leave_group_button")
        tap("confirm_leave_group_button")
        XCTAssertTrue(element("group_overflow_book-club").waitForNonExistence(timeout: 10))
    }

    /// The share sheet is presented from UIKit, not Compose, so nothing else in
    /// the suite would notice if the presenting window were nil.
    func testShareGroupInvite() {
        login()
        tap("settings_tab")
        waitFor("settings_screen")
        tap("manage_groups_button")
        waitFor("group_management_screen")

        // A group you look after opens as its own screen now.
        tap("manage_group_group-a")
        waitFor("group_detail_screen")
        tap("share_invite_group-a")
        let sheet = XCUIApplication().otherElements["ActivityListView"]
        XCTAssertTrue(sheet.waitForExistence(timeout: 10), "share sheet did not appear")
        XCTAssertTrue(
            XCUIApplication().staticTexts
                .containing(NSPredicate(format: "label CONTAINS 'GROUP_A_INVITE'"))
                .firstMatch.exists,
            "share sheet did not carry the invite code"
        )
    }

    /**
     * The message field is multi-line, so its return key inserts a newline and
     * the form is too short to offer a scroll to dismiss with. Without the bar
     * the keyboard covers the form with no way back.
     */
    func testKeyboardCanBeDismissedInThePostForm() {
        login()
        tap("my_posts_tab")
        waitFor("my_posts_screen")
        tap("create_post_fab")
        waitFor("post_form")

        type("Some words", into: "post_message_field")
        XCTAssertTrue(app.keyboards.count > 0, "keyboard should be up while typing")

        // No dedicated dismiss button any more: a tap on empty form space clears
        // focus. The gap just below the title field is above the keyboard and not
        // itself a field, so it lands on the form's own tap-to-dismiss.
        let title = element("post_title_field")
        XCTAssertTrue(title.waitForExistence(timeout: 5), "title field not found")
        title.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 1.0))
            .withOffset(CGVector(dx: 0, dy: 12)).tap()

        let gone = NSPredicate(format: "count == 0")
        expectation(for: gone, evaluatedWith: app.keyboards, handler: nil)
        waitForExpectations(timeout: 5)
    }

    /**
     * Deleting a post, which on iOS is an action sheet rather than a dialog —
     * divergence #3. The sheet is built by the iOS `actual` and drawn nowhere
     * else, so the whole of it is untested by anything on Android.
     */
    func testDeletingAPostGoesThroughTheActionSheet() {
        login()
        createPost(title: "Post to delete")
        tap("my_posts_tab")
        waitFor("my_posts_screen")
        XCTAssertTrue(app.staticTexts["Post to delete"].waitForExistence(timeout: 10))

        tap("post_overflow_button")
        tap("delete_post_button")

        // Cancel first: a destructive confirmation that cannot be backed out of
        // is worse than none, and the cancel is a separate surface on iOS.
        waitFor("confirm_dialog_cancel")
        tap("confirm_dialog_cancel")
        XCTAssertTrue(app.staticTexts["Post to delete"].exists, "cancelling deleted it anyway")

        tap("post_overflow_button")
        tap("delete_post_button")
        waitFor("confirm_delete_button")
        tap("confirm_delete_button")

        XCTAssertTrue(
            app.staticTexts["Post to delete"].waitForNonExistence(timeout: 10),
            "the post survived its own deletion"
        )
    }

    /**
     * The language pickers, which is where the last iOS-only break came from:
     * adding them to registration pushed a field under the keyboard and only
     * iOS noticed.
     */
    func testLanguagePickerAppearsOnlyWhenThereIsAChoice() {
        login()

        // The fixture account reads English alone, so the post form must not
        // ask which language a post is in.
        tap("my_posts_tab")
        waitFor("my_posts_screen")
        tap("create_post_fab")
        waitFor("post_form")
        XCTAssertFalse(element("post_language_field").exists, "asked with only one answer")
        tap("close_form_button")

        // Adding a second language is what makes the question worth asking.
        tap("settings_tab")
        waitFor("settings_screen")
        tap("edit_profile_row")
        waitFor("profile_screen")
        tap("language_ru")
        waitFor("language_ru_selected")
        tap("profile_save")
        tap("profile_back")
        waitFor("settings_screen")

        tap("my_posts_tab")
        waitFor("my_posts_screen")
        tap("create_post_fab")
        waitFor("post_form")
        waitFor("post_language_field")
        XCTAssertTrue(element("post_language_en_selected").exists, "not defaulted to what they write in")
    }

    /**
     * Narrowing the feed by tag: a chip row, which lays out differently here.
     *
     * Seeded the way the favourites test does — as somebody else, because Home
     * does not show you your own posts, so filtering your own would have
     * nothing to filter.
     */
    func testFilteringTheFeedByTag() {
        register(
            name: "Tag",
            surname: "Author",
            email: "ios.tagauthor@example.com",
            groupCode: "GROUP_A_INVITE"
        )
        createPost(title: "A post worth finding")
        logout()

        login()
        XCTAssertTrue(app.staticTexts["A post worth finding"].waitForExistence(timeout: 15))

        tap("tag_filter_wellbeing")
        waitFor("tag_filter_wellbeing_selected")
        XCTAssertTrue(
            app.staticTexts["A post worth finding"].waitForExistence(timeout: 10),
            "filtering by the tag it carries hid it"
        )

        tap("tag_filter_wellbeing_selected")
        XCTAssertTrue(
            element("tag_filter_wellbeing").waitForExistence(timeout: 5),
            "choosing the tag again did not clear it"
        )
    }

    private func register(
        name: String,
        surname: String,
        email: String,
        groupCode: String
    ) {
        tap("register_button")
        waitFor("registration_dialog")
        type(name, into: "register_name_field")
        type(surname, into: "register_surname_field")
        type(email, into: "register_email_field")
        type("password123", into: "register_password_field")
        type(groupCode, into: "register_group_code_field")
        tap("registration_submit_button")
        waitFor("feed_screen", timeout: 10)
    }

    private func createPost(title: String) {
        tap("my_posts_tab")
        waitFor("my_posts_screen")
        tap("create_post_fab")
        type(title, into: "post_title_field")
        type("Created by the iOS integration suite", into: "post_message_field")

        // The group picker only exists for the visibility that needs one.
        tap("visibility_option_group")
        tap("group_selector")
        waitFor("group_option_group-a", timeout: 10)
        tap("group_option_group-a")

        // Tags come from a curated set now: search and take the match, rather
        // than typing a word and confirming it.
        type("heal", into: "tag_input_field")
        waitFor("tag_suggestion_wellbeing", timeout: 10)
        tap("tag_suggestion_wellbeing")
        tap("submit_post_button")
        XCTAssertTrue(app.staticTexts[title].waitForExistence(timeout: 10))
    }

    private func logout() {
        tap("settings_tab")
        waitFor("settings_screen")
        tap("logout_button")
        tap("confirm_sign_out_button")
        waitFor("login_screen")
    }

}

private extension XCUIElement {
    func waitForNonExistence(timeout: TimeInterval) -> Bool {
        let predicate = NSPredicate(format: "exists == false")
        let expectation = XCTNSPredicateExpectation(predicate: predicate, object: self)
        return XCTWaiter.wait(for: [expectation], timeout: timeout) == .completed
    }

    /// What the field is showing.
    ///
    /// Compose on iOS puts a text field's contents in its accessibility label
    /// rather than its value — a password field dumps as
    /// `password_field label: '•••••••••••'` — so both are consulted.
    func contents() -> String {
        let value = (self.value as? String) ?? ""
        return value.isEmpty ? label : value
    }

    func waitForContents(toChangeFrom previous: String, timeout: TimeInterval) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        repeat {
            if contents() != previous { return true }
            Thread.sleep(forTimeInterval: 0.1)
        } while Date() < deadline
        return false
    }
}

/// Screenshot capture, the iOS counterpart of scripts/ui-screenshots.sh.
///
/// simctl can boot, install, launch and screenshot, but it cannot tap or type —
/// there is no `adb shell input text` for the simulator. Driving the app has to
/// happen from inside a UI test, so the capture lives here and the screenshots
/// come out as test attachments.
final class PosterScreenshotTests: PosterTestCase {

    /// Which language to capture in. The app has no in-app language switch, so it
    /// follows the device language — which scripts/ios-screenshots.sh --lang sets
    /// on the simulator. Reading it here (rather than an environment variable)
    /// because xcodebuild does not pass this shell's environment through to the
    /// test runner in the simulator. The content then follows the account signed
    /// in — the demo reader seeded for that language.
    private var language: String {
        (Locale.preferredLanguages.first?.hasPrefix("ru") ?? false) ? "ru" : "en"
    }

    override func setUpWithError() throws {
        // Deliberately NOT applyIntegrationFixtures(): that resets to an empty
        // baseline account, which photographs as a blank feed. scripts/
        // ios-screenshots.sh seeds the demo content first, and this keeps it.
        // POSTER_DEMO_PAYWALL shows the support paywall with placeholder
        // tiers, since there is no store account to configure real ones yet.
        var arguments: [String] = []
        if language != "en" {
            // Reinforce the device language on the app itself; the strings come
            // from composeResources/values-<lang>.
            arguments = ["-AppleLanguages", "(\(language))", "-AppleLocale", "\(language)_\(language.uppercased())"]
        }
        launchApp(extraEnvironment: ["POSTER_DEMO_PAYWALL": "1"], extraArguments: arguments)
    }

    func testCaptureScreens() {
        waitFor("login_screen", timeout: 20)
        shot("00-login")

        tap("register_button")
        waitFor("registration_dialog")
        shot("00b-register")
        tap("close_form_button")

        // The seeded demo reader (see scripts/seed-demo-data.sh) — clearly
        // labelled local test data — whose feed and My Posts are populated.
        // Its language decides which posts the feed returns, so it must match
        // the UI language forced above.
        login(email: "demo.reader.\(language)@example.com", password: "demo123456")

        // Open the post form once up front. That is what first loads the tag
        // catalog, so the feed and My Posts chips below render proper labels
        // ("Looking for work") instead of raw ids ("job_search"); the feed on
        // its own does not trigger the load.
        tap("my_posts_tab")
        waitFor("my_posts_screen")
        tap("create_post_fab")
        waitFor("post_form")
        shot("light-03-add-post")
        tap("close_form_button")
        shot("light-02-my-posts")

        tap("feed_tab")
        waitFor("feed_screen")
        shot("light-01-home")

        // Destructive confirmations become action sheets on iOS (divergence #3).
        tap("my_posts_tab")
        waitFor("my_posts_screen")
        if element("post_overflow_button").exists {
            tap("post_overflow_button")
            tap("delete_post_button")
            shot("light-05-delete-confirmation")
            tap("confirm_dialog_cancel")
        }

        tap("favourites_tab")
        shot("light-06-liked")

        tap("settings_tab")
        waitFor("settings_screen")
        shot("light-07-settings")

        // The support paywall — placeholder tiers in demo mode, so it can be
        // photographed before a store account and real products exist. Wait for
        // a tier to lay out and let the sheet finish sliding up (simulator
        // animations are not disabled) before the shot.
        tap("support_button")
        waitFor("support_paywall")
        waitFor("support_tier_coffee")
        sleep(1)
        shot("light-10-support-paywall")
        app.swipeDown()

        tap("manage_groups_button")
        waitFor("group_management_screen")
        shot("light-08-groups")
        tap("groups_back")

        tap("edit_profile_row")
        waitFor("profile_screen")
        shot("light-11-profile")
        tap("profile_back")

        tap("feed_tab")
        waitFor("feed_screen")
        if element("post_card").exists {
            element("post_card").tap()
            waitFor("details_screen")
            shot("light-09-post-details")
            // The liked-by roster pushes the comments below the fold, and the
            // thread is the last thing on the screen, so scrolling to the end
            // is enough — hit-testing a Compose text after a swipe is not
            // reliable here, so no waiting on it.
            for _ in 0..<4 { app.swipeUp() }
            Thread.sleep(forTimeInterval: 1.0)
            shot("light-09b-post-comments")
            tap("details_back")
        }

        // Dark theme, then the two screens that show the palette best. Turning
        // off "follow device theme" reveals the light/dark selector; then pick
        // dark. (There is no single dark toggle any more.) Skipped wholesale on a
        // partial run that asks for no dark shot — it is the slow tail, and it
        // signs out.
        guard want("dark-07-settings", "dark-01-home", "dark-00-login") else { return }
        tap("settings_tab")
        waitFor("settings_screen")
        // The light/dark selector only shows once "follow device theme" is off.
        // That preference persists between runs, so it may already be off — only
        // toggle when the selector is not already there, else the tap turns
        // follow-device back on and the dark option disappears.
        if !element("theme_dark_option").exists {
            tap("follow_system_theme_toggle")
        }
        tap("theme_dark_option")
        shot("dark-07-settings")
        tap("feed_tab")
        waitFor("feed_screen")
        shot("dark-01-home")

        // The login screen in dark. The theme is an app-level preference that
        // outlives the session, so signing out here lands back on a dark login
        // rather than the light one 00-login caught before sign-in.
        tap("settings_tab")
        waitFor("settings_screen")
        // The expanded theme selector pushes sign-out below the fold, so it is
        // present but not hittable — scroll it into reach before tapping.
        bringIntoReach(element("logout_button"))
        tap("logout_button")
        tap("confirm_sign_out_button")
        waitFor("login_screen")
        shot("dark-00-login")
    }

    /// The shots this run should capture. Empty (the default) means all of them;
    /// scripts/ios-screenshots.sh --only sets TEST_RUNNER_POSTER_ONLY to a
    /// space-separated list, forwarded to the runner by xcodebuild's TEST_RUNNER_
    /// prefix and matched as substrings — so "paywall" catches
    /// "light-10-support-paywall". Recapturing one screen then overwrites only
    /// that file and leaves the rest of the set alone.
    private lazy var only: [String] =
        (ProcessInfo.processInfo.environment["POSTER_ONLY"] ?? "")
            .split(separator: " ").map(String.init)

    private func want(_ names: String...) -> Bool {
        if only.isEmpty { return true }
        return names.contains { name in only.contains { name.contains($0) } }
    }

    private func shot(_ name: String) {
        guard want(name) else { return }
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

}
