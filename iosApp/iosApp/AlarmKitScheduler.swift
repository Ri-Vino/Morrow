import ActivityKit
import AlarmKit
import Foundation
import Shared
import SwiftUI

/// Morrow's alarms in AlarmKit, which rings them like the Clock app's, even with Morrow closed.
final class AlarmKitScheduler: NSObject, AlarmKitBridge {
    private let manager = AlarmManager.shared

    /// AlarmKit schedules asynchronously, so changes queue up in order, and ids being handed over count as scheduled.
    private var queue: Task<Void, Never>?
    private var pending: [Int64: Int] = [:]

    var authorized: Bool { manager.authorizationState == .authorized }

    var canAsk: Bool { manager.authorizationState == .notDetermined }

    func requestAuthorization(onResult: @escaping (KotlinBoolean) -> Void) {
        Task { @MainActor in
            let state = try? await manager.requestAuthorization()
            onResult(KotlinBoolean(value: state == .authorized))
        }
    }

    func schedule(id: Int64, label: String, hour: Int32, minute: Int32, isoDays: [KotlinInt], sound: String) {
        let days = isoDays.compactMap { Self.weekday($0.int32Value) }
        let time = Alarm.Schedule.Relative.Time(hour: Int(hour), minute: Int(minute))
        let schedule = Alarm.Schedule.relative(.init(time: time, repeats: days.isEmpty ? .never : .weekly(days)))
        let alert = AlarmPresentation.Alert(
            title: LocalizedStringResource(stringLiteral: label),
            stopButton: AlarmButton(text: "Stop", textColor: .white, systemImageName: "stop.fill"),
            secondaryButton: AlarmButton(text: "Start mission", textColor: .white, systemImageName: "sun.max.fill"),
            secondaryButtonBehavior: .custom
        )
        let attributes = AlarmAttributes<MorrowAlarm>(presentation: AlarmPresentation(alert: alert), tintColor: Self.sunrise)
        let configuration = AlarmManager.AlarmConfiguration.alarm(
            schedule: schedule,
            attributes: attributes,
            secondaryIntent: StartMissionIntent(alarmID: String(id)),
            sound: .named(sound)
        )
        let alarmID = Self.uuid(for: id)
        pending[id, default: 0] += 1
        enqueue {
            try? AlarmManager.shared.cancel(id: alarmID)
            do {
                _ = try await AlarmManager.shared.schedule(id: alarmID, configuration: configuration)
            } catch {
                print("Morrow: couldn't schedule alarm \(id): \(error)")
            }
        } then: { [weak self] in
            self?.settle(id)
        }
    }

    func cancel(id: Int64) {
        let alarmID = Self.uuid(for: id)
        enqueue { try? AlarmManager.shared.cancel(id: alarmID) } then: {}
    }

    func scheduledIds() -> [KotlinLong] {
        let held = ((try? manager.alarms) ?? []).compactMap { Self.morrowID($0.id) }
        return Set(held).union(pending.keys).map { KotlinLong(value: $0) }
    }

    private func enqueue(_ work: @escaping () async -> Void, then done: @escaping @MainActor () -> Void) {
        let previous = queue
        queue = Task { @MainActor in
            await previous?.value
            await work()
            done()
        }
    }

    private func settle(_ id: Int64) {
        let count = (pending[id] ?? 1) - 1
        pending[id] = count > 0 ? count : nil
    }

    /// Morrow's ids are small whole numbers; AlarmKit's are UUIDs. This prefix marks Morrow's.
    private static let prefix = "4D4F5252-4F57-4000-8000-"

    private static func uuid(for id: Int64) -> UUID {
        UUID(uuidString: prefix + String(format: "%012llX", id))!
    }

    private static func morrowID(_ uuid: UUID) -> Int64? {
        let text = uuid.uuidString
        guard text.hasPrefix(prefix) else { return nil }
        return Int64(text.dropFirst(prefix.count), radix: 16)
    }

    private static func weekday(_ iso: Int32) -> Locale.Weekday? {
        switch iso {
        case 1: return .monday
        case 2: return .tuesday
        case 3: return .wednesday
        case 4: return .thursday
        case 5: return .friday
        case 6: return .saturday
        case 7: return .sunday
        default: return nil
        }
    }

    /// Pip's sun, #F5A65B (Illustration.sun), the Ring screen's accent, for the alarm's buttons.
    private static let sunrise = Color(red: 245 / 255, green: 166 / 255, blue: 91 / 255)
}

struct MorrowAlarm: AlarmMetadata {}
