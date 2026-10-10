import AppIntents
import Shared

/// The alarm's "Start mission" button. The system owns the alarm screen, so this opens Morrow straight into the mission.
struct StartMissionIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Start mission"
    static let openAppWhenRun = true
    static let isDiscoverable = false

    @Parameter(title: "Alarm")
    var alarmID: String

    init() {}

    init(alarmID: String) {
        self.alarmID = alarmID
    }

    @MainActor
    func perform() async throws -> some IntentResult {
        if let id = Int64(alarmID) {
            IosApp.shared.wake(alarmId: id)
        }
        return .result()
    }
}
