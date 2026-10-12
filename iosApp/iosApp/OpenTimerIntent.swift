import AlarmKit
import AppIntents
import Shared

/// The timer alert's "Open" button: stops the alert and opens Morrow on the timer's done screen, where Pip reads its note.
struct OpenTimerIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Open"
    static let openAppWhenRun = true
    static let isDiscoverable = false

    /// The timer's alarm in AlarmKit, to stop.
    @Parameter(title: "Alarm")
    var alarmID: String

    /// Morrow's timer.
    @Parameter(title: "Timer")
    var timerID: String

    init() {}

    init(alarmID: String, timerID: String) {
        self.alarmID = alarmID
        self.timerID = timerID
    }

    @MainActor
    func perform() async throws -> some IntentResult {
        // Morrow takes the timer first, so stopping the alert doesn't look like Stop was tapped on it.
        if let id = Int64(timerID) {
            IosApp.shared.wakeTimer(timerId: id)
        }
        // A custom button doesn't stop the alert by itself. Stopping twice is harmless, so this is safe to run again.
        if let alarm = UUID(uuidString: alarmID) {
            try? AlarmManager.shared.stop(id: alarm)
        }
        return .result()
    }
}
