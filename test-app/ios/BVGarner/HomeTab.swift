import SwiftUI

/// Home: is this device ready for pushes? A banner (n of 5), the checklist with one action
/// per problem, and the test push steps.
struct HomeTab: View {
    @ObservedObject var model: AppModel

    var body: some View {
        let snapshot = model.snapshot
        let quiet = snapshot.quiet
        let checks = Checklist.evaluate(HealthInput(
            initialized: snapshot.state.initialized,
            deviceId: snapshot.state.deviceId,
            tokenSynced: snapshot.diag?.tokenSynced == true,
            hasSession: snapshot.signedIn,
            notificationsAllowed: snapshot.notificationsAllowed,
            quiet: quiet,
            slow: snapshot.slow
        ))
        TabScroll {
            banner(checks)
            Card(title: UiText.CHECKLIST) {
                ForEach(checks, id: \.kind) { check in row(check, snapshot, quiet) }
            }
            Card(title: UiText.TEST_PUSH_TITLE) {
                Helper(UiText.TEST_PUSH_STEPS)
                BvButton(
                    label: snapshot.state.deviceId == nil ? UiText.COPY_DEVICE_ID_WAITING : UiText.COPY_DEVICE_ID,
                    enabled: snapshot.state.deviceId != nil,
                    action: copyDeviceId
                )
                .accessibilityIdentifier("copyDeviceId")
            }
        }
    }

    private func banner(_ checks: [Check]) -> some View {
        let passed = Checklist.passed(checks)
        let total = checks.count
        let summary = Checklist.summary(checks)
        let look: (tone: Tone, title: String, detail: String, mark: Mark)
        switch summary {
        case .ready: look = (.ok, UiText.bannerReady(total), UiText.BANNER_READY_DETAIL, .ok)
        case .action: look = (.wait, UiText.bannerAction(passed, total), UiText.BANNER_ACTION_DETAIL, .bad)
        case .checking: look = (.wait, UiText.bannerChecking(passed, total), UiText.BANNER_CHECKING_DETAIL, .wait)
        }
        let tone = look.tone
        return Card(fill: BvColors.soft(tone)) {
            HStack(alignment: .top, spacing: 12) {
                Badge(text: look.mark.symbol, fill: BvColors.strong(tone), size: 36, textSize: 18)
                VStack(alignment: .leading, spacing: 0) {
                    Text(look.title).font(.system(size: 16, weight: .bold)).foregroundColor(BvColors.strong(tone))
                    Text(look.detail).font(.system(size: 13)).foregroundColor(BvColors.textSecondary)
                    ProgressLine(done: passed, total: total, color: BvColors.strong(tone))
                }
            }
        }
        .accessibilityIdentifier("banner")
    }

    @ViewBuilder
    private func row(_ check: Check, _ snapshot: Snapshot, _ quiet: QuietStatus) -> some View {
        let mark = Mark.of(check.mark)
        switch check.kind {
        case .registered:
            switch check.mark {
            case .ok:
                CheckRow(mark: mark, title: UiText.REGISTERED_OK, detail: UiText.registeredDetail(snapshot.state.deviceId ?? ""),
                         actionLabel: UiText.COPY, showAction: true, action: copyDeviceId)
            case .wait:
                CheckRow(mark: mark, title: UiText.REGISTERED_WAIT,
                         detail: snapshot.slow ? UiText.REGISTERED_SLOW_DETAIL : UiText.REGISTERED_WAIT_DETAIL)
            case .bad:
                CheckRow(mark: mark, title: UiText.REGISTERED_BAD, detail: UiText.REGISTERED_BAD_DETAIL)
            }
        case .pushToken:
            switch check.mark {
            case .ok: CheckRow(mark: mark, title: UiText.TOKEN_OK, detail: UiText.TOKEN_OK_DETAIL)
            case .wait: CheckRow(mark: mark, title: UiText.TOKEN_WAIT, detail: UiText.TOKEN_WAIT_DETAIL)
            case .bad: CheckRow(mark: mark, title: UiText.TOKEN_BAD, detail: UiText.TOKEN_BAD_DETAIL)
            }
        case .signedIn:
            switch check.mark {
            case .ok:
                CheckRow(mark: mark, title: UiText.SIGNED_IN_OK,
                         detail: model.prefs.email.isEmpty ? UiText.SIGNED_IN_OK_DETAIL : model.prefs.email)
            case .wait:
                CheckRow(mark: mark, title: UiText.SIGNED_IN_WAIT, detail: UiText.SIGNED_IN_WAIT_DETAIL)
            case .bad:
                CheckRow(mark: mark, title: UiText.SIGNED_IN_BAD, detail: UiText.SIGNED_IN_BAD_DETAIL,
                         actionLabel: UiText.ACTION_SIGN_IN, filledAction: true, showAction: true) {
                    model.selected = .ACCOUNT
                }
            }
        case .notifications:
            if check.mark == .ok {
                CheckRow(mark: mark, title: UiText.NOTIFICATIONS_OK, detail: UiText.NOTIFICATIONS_OK_DETAIL)
            } else {
                CheckRow(mark: mark, title: UiText.NOTIFICATIONS_BAD, detail: UiText.NOTIFICATIONS_BAD_DETAIL,
                         actionLabel: UiText.ACTION_ALLOW, filledAction: true, showAction: true) {
                    model.allowNotifications()
                }
            }
        case .quietHours:
            quietRow(mark, quiet)
        }
    }

    @ViewBuilder
    private func quietRow(_ mark: Mark, _ quiet: QuietStatus) -> some View {
        let change = { model.selected = .ACCOUNT }
        if quiet.mode == .unknown {
            CheckRow(mark: mark, title: UiText.QUIET_UNKNOWN_TITLE, detail: UiText.QUIET_UNKNOWN_DETAIL)
        } else if let window = quiet.window, quiet.mode != .off {
            if quiet.mode == .onNow {
                CheckRow(mark: mark, title: UiText.QUIET_NOW_TITLE, detail: UiText.quietNowDetail(QuietText.end(window)),
                         actionLabel: UiText.ACTION_CHANGE, showAction: true, action: change)
            } else {
                CheckRow(mark: mark, title: UiText.quietLaterTitle(QuietText.range(window)), detail: UiText.QUIET_LATER_DETAIL,
                         actionLabel: UiText.ACTION_CHANGE, showAction: true, action: change)
            }
        } else {
            CheckRow(mark: mark, title: UiText.QUIET_OFF_TITLE, detail: UiText.QUIET_OFF_DETAIL,
                     actionLabel: UiText.ACTION_CHANGE, showAction: true, action: change)
        }
    }

    private func copyDeviceId() {
        guard let id = model.snapshot.state.deviceId else { return }
        model.copy(UiText.DEVICE_ID, id)
    }
}
