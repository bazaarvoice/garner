import SwiftUI

/// Inbox: every push the SDK reported on this device (`PushHistory`), newest first, with
/// filters, the SDK steps of each push and Clear. Tapping a card copies its notification id.
struct InboxTab: View {
    @ObservedObject var model: AppModel
    @State private var filter = PushFilter.all
    @State private var confirmingClear = false

    var body: some View {
        let records = model.records
        let shown = records.filter { filter.matches($0) }
        let zone = TimeZone.current
        let now = model.snapshot.nowMillis
        TabScroll {
            HStack(spacing: 6) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(PushFilter.allCases, id: \.self) { option in
                            FilterChip(
                                text: UiText.filterChip(option.label, PushRecords.count(records, option)),
                                selected: option == filter
                            ) { filter = option }
                        }
                    }
                }
                FilterChip(text: UiText.CLEAR, selected: false, color: BvColors.bad) {
                    if !records.isEmpty { confirmingClear = true }
                }
                .accessibilityIdentifier("clearInbox")
            }
            .padding(.top, 12)
            if shown.isEmpty {
                Helper(records.isEmpty ? UiText.INBOX_EMPTY : UiText.INBOX_EMPTY_FILTER).padding(.top, 20)
            } else {
                ForEach(shown) { record in card(record, now: now, zone: zone) }
            }
        }
        .alert(UiText.CLEAR_TITLE, isPresented: $confirmingClear) {
            Button(UiText.CLEAR, role: .destructive) { model.clearInbox() }
            Button(UiText.CANCEL, role: .cancel) {}
        } message: {
            Text(UiText.clearMessage(records.count))
        }
    }

    private func card(_ record: PushRecord, now: Int64, zone: TimeZone) -> some View {
        Card {
            HStack(alignment: .top) {
                Text(PushLabels.title(record))
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(BvColors.text)
                    .lineLimit(2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(TimeText.inbox(record.firstAtMillis, now: now, zone))
                    .font(.system(size: 12))
                    .foregroundColor(BvColors.textSecondary)
                    .padding(.leading, 8)
                    .padding(.top, 2)
            }
            Helper(PushLabels.body(record))
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(PushLabels.chips(record, zone), id: \.self) { Chip(text: $0.text, tone: $0.tone) }
                }
            }
            .padding(.top, 10)
        }
        .contentShape(Rectangle())
        .onTapGesture { model.copy(UiText.NOTIFICATION_ID, record.id) }
    }
}
