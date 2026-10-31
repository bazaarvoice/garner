import SwiftUI

/// Navy top bar (title and chips) over the selected tab, the tab bar with the Inbox badge,
/// and a short toast for "… copied" and settings hints.
struct Shell: View {
    @ObservedObject var model: AppModel

    var body: some View {
        TabView(selection: $model.selected) {
            ForEach(TabId.allCases) { tab in
                page(tab)
                    .tabItem { Label(tab.label, systemImage: tab.icon) }
                    .tag(tab)
                    .badge(tab == .INBOX ? model.badgeText : nil)
            }
        }
        .tint(BvColors.primary)
        .overlay(alignment: .bottom) { toast }
        .preferredColorScheme(.light)
    }

    private func page(_ tab: TabId) -> some View {
        VStack(spacing: 0) {
            TopBar(title: model.title(for: tab), chips: model.chips(for: tab))
            content(tab).frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(BvColors.background)
    }

    @ViewBuilder
    private func content(_ tab: TabId) -> some View {
        switch tab {
        case .HOME: HomeTab(model: model)
        case .ACCOUNT: AccountTab(model: model)
        case .INBOX: InboxTab(model: model)
        case .DETAILS: DetailsTab(model: model)
        }
    }

    @ViewBuilder
    private var toast: some View {
        if let message = model.toast {
            Text(message)
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(.white)
                .padding(EdgeInsets(top: 10, leading: 16, bottom: 10, trailing: 16))
                .background(Capsule().fill(Color.black.opacity(0.82)))
                .padding(.bottom, 64)
                .transition(.opacity)
                .accessibilityIdentifier("toast")
        }
    }
}

/// Title 22 pt bold on the dark bar; it also runs under the status bar.
struct TopBar: View {
    let title: String
    let chips: [ToneText]

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.system(size: 22, weight: .bold))
                .foregroundColor(BvColors.onPrimary)
                .accessibilityAddTraits(.isHeader)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(chips, id: \.self) { Chip(text: $0.text, tone: $0.tone, onBar: true) }
                }
            }
        }
        .padding(EdgeInsets(top: 12, leading: 16, bottom: 14, trailing: 16))
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(BvColors.topBar.ignoresSafeArea(edges: .top))
    }
}
