import ActivityKit
import AlarmKit
import SwiftUI
import WidgetKit

/// AlarmKit expects a Live Activity for countdowns, or it may dismiss them without ringing.
@main
struct MorrowWidgets: WidgetBundle {
    var body: some Widget {
        TimerActivity()
    }
}

/// A running or paused timer on the Lock Screen and in the Dynamic Island. AlarmKit draws the alert itself.
struct TimerActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: AlarmAttributes<MorrowAlarm>.self) { context in
            HStack {
                Image(systemName: "timer")
                    .foregroundStyle(context.attributes.tintColor)
                TimerTitle(attributes: context.attributes)
                Spacer()
                TimeLeft(state: context.state)
                    .font(.title.monospacedDigit())
            }
            .padding()
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    TimerTitle(attributes: context.attributes)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    TimeLeft(state: context.state)
                        .font(.title2.monospacedDigit())
                }
            } compactLeading: {
                Image(systemName: "timer")
                    .foregroundStyle(context.attributes.tintColor)
            } compactTrailing: {
                TimeLeft(state: context.state)
                    .monospacedDigit()
                    .frame(maxWidth: 64)
            } minimal: {
                Image(systemName: "timer")
                    .foregroundStyle(context.attributes.tintColor)
            }
            .keylineTint(context.attributes.tintColor)
        }
    }
}

/// The timer's note, or "Timer".
private struct TimerTitle: View {
    let attributes: AlarmAttributes<MorrowAlarm>

    var body: some View {
        Text(attributes.presentation.countdown?.title ?? attributes.presentation.alert.title)
            .lineLimit(1)
    }
}

/// The time left, counting down, or held while paused.
private struct TimeLeft: View {
    let state: AlarmPresentationState

    var body: some View {
        switch state.mode {
        case .countdown(let countdown):
            Text(countdown.fireDate, style: .timer)
        case .paused(let paused):
            Text(Self.clock(paused.totalCountdownDuration - paused.previouslyElapsedDuration))
        default:
            EmptyView()
        }
    }

    /// Like Morrow's clockText: "04:12", or "1:02:03" from an hour, rounded up.
    private static func clock(_ left: TimeInterval) -> String {
        let seconds = max(0, Int(left.rounded(.up)))
        let hours = seconds / 3600
        let clock = String(format: "%02d:%02d", seconds / 60 % 60, seconds % 60)
        return hours > 0 ? "\(hours):\(clock)" : clock
    }
}
