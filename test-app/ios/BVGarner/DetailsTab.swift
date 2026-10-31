import SwiftUI

/// Details: every data point an integrator asks for, each with Copy; Copy all as JSON and
/// Share; the recent SDK events; and the raw inspector text.
struct DetailsTab: View {
    @ObservedObject var model: AppModel
    @State private var showRaw = true

    var body: some View {
        let values = model.reportValues()
        let json = DetailsReport.toJson(values)
        TabScroll {
            Card {
                ForEach(ReportSection.allCases, id: \.self) { section in
                    SectionLabel(text: section.title, first: section == ReportSection.allCases.first)
                    ForEach(values.filter { $0.field.section == section }, id: \.field) { item in
                        KeyValueRow(
                            label: item.field.label,
                            value: item.value,
                            onCopy: item.field.copyable ? { model.copy(item.field.label, item.value) } : nil
                        )
                    }
                }
            }
            HStack(spacing: 10) {
                BvButton(label: UiText.COPY_ALL) { model.copy(UiText.CLIP_DETAILS, json) }
                    .accessibilityIdentifier("copyAll")
                ShareLink(item: json, subject: Text(UiText.CLIP_DETAILS), message: Text(UiText.SHARE_TITLE)) {
                    Text(UiText.SHARE)
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(BvColors.primary)
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .background(RoundedRectangle(cornerRadius: 10).fill(BvColors.card))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(BvColors.primary, lineWidth: 2))
                }
                .padding(.top, 12)
            }
            Card(title: UiText.EVENTS_TITLE) {
                Helper(UiText.EVENTS_HELP)
                VStack(alignment: .leading, spacing: 0) {
                    if model.events.isEmpty {
                        Text(UiText.EVENTS_EMPTY).font(.system(size: 14)).foregroundColor(BvColors.textSecondary)
                    } else {
                        ForEach(model.events) { entry in
                            HStack(alignment: .top, spacing: 12) {
                                Text(TimeText.clock(entry.atMillis, .current))
                                    .font(.system(size: 13, design: .monospaced))
                                    .foregroundColor(BvColors.textSecondary)
                                Text(entry.line.text)
                                    .font(.system(size: 14))
                                    .foregroundColor(BvColors.strong(entry.line.tone))
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                            .padding(.vertical, 5)
                        }
                    }
                }
                .padding(.top, 8)
            }
            Card {
                HStack {
                    Text(UiText.RAW_TITLE).font(.system(size: 17, weight: .bold)).foregroundColor(BvColors.text)
                    Spacer()
                    Button(showRaw ? UiText.HIDE : UiText.SHOW) { showRaw.toggle() }
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(BvColors.primary)
                        .padding(.vertical, 6)
                }
                Helper(UiText.RAW_HELP)
                if showRaw {
                    Text(model.inspector)
                        .font(.system(size: 12, design: .monospaced))
                        .foregroundColor(BvColors.text)
                        .textSelection(.enabled)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(EdgeInsets(top: 10, leading: 12, bottom: 10, trailing: 12))
                        .background(RoundedRectangle(cornerRadius: 8).fill(BvColors.code))
                        .padding(.top, 8)
                        .accessibilityIdentifier("inspector")
                }
            }
        }
    }
}
