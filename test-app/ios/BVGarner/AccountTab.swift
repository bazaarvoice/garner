import BVGarner
import SwiftUI

/// Account: sign in with a user id and the consumer email (both required), the signed-in user
/// and session, receive pushes (opt in / opt out), quiet hours, and sign out.
struct AccountTab: View {
    @ObservedObject var model: AppModel
    @State private var userId = ""
    @State private var email = ""
    @State private var filled = false
    @State private var pickingQuiet = false
    @FocusState private var focused: Bool

    var body: some View {
        let signedIn = model.snapshot.signedIn
        TabScroll {
            if signedIn {
                profileCard
                sessionCard
            } else {
                signInCard
            }
            prefsCard
            if signedIn {
                signOutCard
            } else {
                Card(title: UiText.NEXT_TITLE) { Helper(UiText.NEXT_STEPS) }
            }
        }
        .onAppear {
            guard !filled else { return }
            filled = true
            userId = model.prefs.userId
            email = model.prefs.email
        }
        .sheet(isPresented: $pickingQuiet) {
            QuietHoursSheet(initial: model.snapshot.quiet.window ?? .default) { window in
                model.setQuiet(window)
            }
        }
    }

    private var signInCard: some View {
        Card(title: UiText.SIGN_IN_TITLE) {
            Helper(UiText.SIGN_IN_HELP)
            FieldLabel(UiText.LABEL_USER_ID)
            BvInput(hint: UiText.HINT_USER_ID, text: $userId, contentType: .username)
                .focused($focused)
                .accessibilityIdentifier("userId")
            FieldLabel(UiText.LABEL_EMAIL)
            BvInput(hint: UiText.HINT_EMAIL, text: $email, keyboard: .emailAddress, contentType: .emailAddress)
                .focused($focused)
                .accessibilityIdentifier("email")
            ResultText(result: model.formError)
            BvButton(label: UiText.SIGN_IN_BUTTON) {
                focused = false
                model.signIn(userId: userId, email: email)
            }
            .accessibilityIdentifier("signIn")
            ResultText(result: model.signInResult)
            Helper(UiText.SIGN_IN_NOTE).padding(.top, 8)
        }
    }

    private var profileCard: some View {
        let email = model.prefs.email
        let user = model.prefs.userId
        let since = model.prefs.signedInAt
        return Card {
            HStack(spacing: 14) {
                Badge(text: AppInfo.initials(email: email, userId: user), fill: BvColors.primary, size: 48, textSize: 18)
                VStack(alignment: .leading, spacing: 2) {
                    Text(email.isEmpty ? UiText.SIGNED_IN_USER_UNKNOWN : email)
                        .font(.system(size: 16, weight: .bold)).foregroundColor(BvColors.text)
                    if !user.isEmpty {
                        Text(UiText.userIdLine(user)).font(.system(size: 13)).foregroundColor(BvColors.textSecondary)
                    }
                    Text(since > 0 ? UiText.signedInLine(TimeText.clock(since, .current)) : UiText.SIGNED_IN_LINE_NO_TIME)
                        .font(.system(size: 13)).foregroundColor(BvColors.textSecondary)
                }
            }
        }
    }

    private var sessionCard: some View {
        let diag = model.snapshot.diag
        let since = model.prefs.signedInAt
        return Card(title: UiText.SESSION_TITLE) {
            KeyValueRow(label: UiText.ROW_IDENTITY, value: diag?.identityPhase ?? UiText.UNKNOWN)
            KeyValueRow(
                label: UiText.ROW_SESSION_UNTIL,
                value: TimeText.epochMillis(diag?.sessionExpiry).map { TimeText.dateTime($0, .current) } ?? UiText.NONE
            )
            KeyValueRow(label: UiText.ROW_SIGNED_IN_AT, value: since > 0 ? TimeText.dateTime(since, .current) : UiText.NONE)
        }
    }

    private var prefsCard: some View {
        let diag = model.snapshot.diag
        return Card(title: UiText.PREFS_TITLE) {
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 0) {
                    Text(UiText.RECEIVE_PUSHES).font(.system(size: 15, weight: .bold)).foregroundColor(BvColors.text)
                    Helper(UiText.RECEIVE_PUSHES_HELP)
                    Helper(UiText.serverOptLine(diag?.serverOptedOut))
                }
                Toggle("", isOn: Binding(get: { model.receivePushes }, set: { model.setReceivePushes($0) }))
                    .labelsHidden()
                    .tint(BvColors.primary)
                    .disabled(diag == nil)
                    .accessibilityIdentifier("receivePushes")
            }
            .padding(.top, 10)
            BvDivider()
            Text(UiText.QUIET_TITLE).font(.system(size: 15, weight: .bold)).foregroundColor(BvColors.text)
            Text(QuietText.summary(model.snapshot.quiet))
                .font(.system(size: 14)).foregroundColor(BvColors.text).padding(.top, 4)
            Helper(UiText.QUIET_HELP)
            HStack(spacing: 8) {
                SmallButton(label: UiText.QUIET_OFF) { model.quietOff() }
                SmallButton(label: UiText.QUIET_DEFAULT) { model.quietDefault() }
                SmallButton(label: UiText.QUIET_SET) { pickingQuiet = true }
            }
            .padding(.top, 8)
            ResultText(result: model.quietResult)
        }
    }

    private var signOutCard: some View {
        Card {
            Helper(UiText.SIGN_OUT_HELP)
            BvButton(label: UiText.SIGN_OUT, kind: .danger) { model.signOut() }
                .accessibilityIdentifier("signOut")
            ResultText(result: model.signOutResult)
        }
    }
}

/// Start and end time pickers (24-hour), like Android's two TimePickerDialogs.
struct QuietHoursSheet: View {
    let initial: BVPush.QuietWindow
    let onSave: (BVPush.QuietWindow) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var start = Date()
    @State private var end = Date()
    @State private var loaded = false

    var body: some View {
        NavigationStack {
            Form {
                DatePicker(UiText.QUIET_START_TITLE, selection: $start, displayedComponents: .hourAndMinute)
                DatePicker(UiText.QUIET_END_TITLE, selection: $end, displayedComponents: .hourAndMinute)
            }
            .environment(\.locale, Locale(identifier: "en_GB"))
            .navigationTitle(UiText.QUIET_TITLE)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(UiText.CANCEL) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(UiText.SAVE) {
                        let from = Self.parts(start)
                        let to = Self.parts(end)
                        onSave(BVPush.QuietWindow(startHour: from.0, startMinute: from.1, endHour: to.0, endMinute: to.1))
                        dismiss()
                    }
                }
            }
            .onAppear {
                guard !loaded else { return }
                loaded = true
                start = Self.date(initial.startHour, initial.startMinute)
                end = Self.date(initial.endHour, initial.endMinute)
            }
        }
        .presentationDetents([.medium])
    }

    private static func date(_ hour: Int, _ minute: Int) -> Date {
        Calendar.current.date(bySettingHour: hour, minute: minute, second: 0, of: Date()) ?? Date()
    }

    private static func parts(_ date: Date) -> (Int, Int) {
        let value = Calendar.current.dateComponents([.hour, .minute], from: date)
        return (value.hour ?? 0, value.minute ?? 0)
    }
}
