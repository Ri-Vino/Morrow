import ActivityKit
import AlarmKit
import Foundation
import Shared
import SwiftUI

/// Morrow's alarms and timers in AlarmKit, which rings them like the Clock app's, even with Morrow closed.
final class AlarmKitScheduler: NSObject, AlarmKitBridge {
    private let manager = AlarmManager.shared

    /// AlarmKit schedules asynchronously, so changes queue up in order, and ids being handed over count as scheduled.
    private var queue: Task<Void, Never>?
    private var pending: [Int64: Int] = [:]
    /// Ids AlarmKit refused when last handed them, so Morrow knows they never rang.
    private var refused: Set<Int64> = []
    private var watching: Task<Void, Never>?

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
        let time = AlarmKit.Alarm.Schedule.Relative.Time(hour: Int(hour), minute: Int(minute))
        let schedule = AlarmKit.Alarm.Schedule.relative(.init(time: time, repeats: days.isEmpty ? .never : .weekly(days)))
        submit(id: id, alarmID: id, label: label, schedule: schedule, sound: sound)
    }

    /// One date of an alarm on another city's clock. Its "Start mission" button wakes `alarmId`, the alarm it belongs to.
    func scheduleOnce(id: Int64, alarmId: Int64, label: String, epochSeconds: Int64, sound: String) {
        let schedule = AlarmKit.Alarm.Schedule.fixed(Date(timeIntervalSince1970: TimeInterval(epochSeconds)))
        submit(id: id, alarmID: alarmId, label: label, schedule: schedule, sound: sound)
    }

    private func submit(id: Int64, alarmID morrowAlarm: Int64, label: String, schedule: AlarmKit.Alarm.Schedule, sound: String) {
        let alert = AlarmPresentation.Alert(
            title: LocalizedStringResource(stringLiteral: label),
            stopButton: Self.stopButton,
            secondaryButton: AlarmButton(text: "Start mission", textColor: .white, systemImageName: "sun.max.fill"),
            secondaryButtonBehavior: .custom
        )
        let attributes = AlarmAttributes<MorrowAlarm>(presentation: AlarmPresentation(alert: alert), tintColor: Self.sunrise)
        let configuration = AlarmManager.AlarmConfiguration.alarm(
            schedule: schedule,
            attributes: attributes,
            secondaryIntent: StartMissionIntent(alarmID: String(morrowAlarm)),
            sound: .named(sound)
        )
        handOver(id: id, configuration: configuration)
    }

    /// A timer: a countdown on the Lock Screen and in the Dynamic Island (MorrowWidgets draws it), then an alert whose
    /// "Open" button opens Morrow on the timer, where Pip reads its note.
    func startCountdown(id: Int64, timerId: Int64, title: String, seconds: Double, sound: String) {
        handOver(id: id, configuration: Self.countdown(id: id, timerId: timerId, title: title, seconds: seconds, sound: sound))
    }

    func pauseCountdown(id: Int64, timerId: Int64, title: String, seconds: Double, sound: String) {
        let alarmID = Self.uuid(for: id)
        let configuration = Self.countdown(id: id, timerId: timerId, title: title, seconds: seconds, sound: sound)
        let outcome = HandOver()
        pending[id, default: 0] += 1
        enqueue {
            let counting = ((try? AlarmManager.shared.alarms) ?? []).contains { $0.id == alarmID && $0.state == .countdown }
            if !counting {
                // Lost, or given a minute while paused: a new countdown for what's left, held straight away.
                try? AlarmManager.shared.cancel(id: alarmID)
                do {
                    _ = try await AlarmManager.shared.schedule(id: alarmID, configuration: configuration)
                } catch {
                    print("Morrow: couldn't hand over timer \(timerId): \(error)")
                    outcome.refused = true
                    return
                }
            }
            try? AlarmManager.shared.pause(id: alarmID)
        } then: { [weak self] in
            self?.settle(id, refused: outcome.refused)
        }
    }

    private static func countdown(id: Int64, timerId: Int64, title: String, seconds: Double, sound: String)
        -> AlarmManager.AlarmConfiguration<MorrowAlarm>
    {
        let name = LocalizedStringResource(stringLiteral: title)
        let alert = AlarmPresentation.Alert(
            title: name,
            stopButton: stopButton,
            secondaryButton: AlarmButton(text: "Open", textColor: .white, systemImageName: "sun.max.fill"),
            secondaryButtonBehavior: .custom
        )
        // Pausing and resuming happen in Morrow, which keeps the time left; these only label the system's own views.
        let presentation = AlarmPresentation(
            alert: alert,
            countdown: AlarmPresentation.Countdown(title: name),
            paused: AlarmPresentation.Paused(
                title: "Paused",
                resumeButton: AlarmButton(text: "Resume", textColor: .white, systemImageName: "play.fill")
            )
        )
        let attributes = AlarmAttributes<MorrowAlarm>(presentation: presentation, tintColor: sunrise)
        return .timer(
            duration: max(seconds, 1),
            attributes: attributes,
            secondaryIntent: OpenTimerIntent(alarmID: uuid(for: id).uuidString, timerID: String(timerId)),
            sound: .named(sound)
        )
    }

    private func handOver(id: Int64, configuration: AlarmManager.AlarmConfiguration<MorrowAlarm>) {
        let alarmID = Self.uuid(for: id)
        let outcome = HandOver()
        pending[id, default: 0] += 1
        enqueue {
            try? AlarmManager.shared.cancel(id: alarmID)
            do {
                _ = try await AlarmManager.shared.schedule(id: alarmID, configuration: configuration)
            } catch {
                // AlarmKit has no fixed limit on alarms, but may refuse more with maximumLimitReached.
                print("Morrow: couldn't schedule alarm \(id): \(error)")
                outcome.refused = true
            }
        } then: { [weak self] in
            self?.settle(id, refused: outcome.refused)
        }
    }

    func cancel(id: Int64) {
        let alarmID = Self.uuid(for: id)
        refused.remove(id)
        enqueue { try? AlarmManager.shared.cancel(id: alarmID) } then: {}
    }

    func scheduledIds() -> [KotlinLong] {
        let held = ((try? manager.alarms) ?? []).compactMap { Self.morrowID($0.id) }
        return Set(held).union(pending.keys).map { KotlinLong(value: $0) }
    }

    func countingDownIds() -> [KotlinLong] { ids { $0.state == .countdown } }

    func alertingIds() -> [KotlinLong] { ids { $0.state == .alerting } }

    func refusedIds() -> [KotlinLong] { refused.map { KotlinLong(value: $0) } }

    private func ids(where wanted: (AlarmKit.Alarm) -> Bool) -> [KotlinLong] {
        ((try? manager.alarms) ?? []).filter(wanted).compactMap { Self.morrowID($0.id) }.map { KotlinLong(value: $0) }
    }

    func watch(onChange: @escaping () -> Void) {
        watching?.cancel()
        watching = Task { @MainActor in
            for await _ in AlarmManager.shared.alarmUpdates {
                onChange()
            }
        }
    }

    private func enqueue(_ work: @escaping () async -> Void, then done: @escaping @MainActor () -> Void) {
        let previous = queue
        queue = Task { @MainActor in
            await previous?.value
            await work()
            done()
        }
    }

    private func settle(_ id: Int64, refused wasRefused: Bool) {
        let count = (pending[id] ?? 1) - 1
        pending[id] = count > 0 ? count : nil
        if wasRefused { refused.insert(id) } else { refused.remove(id) }
    }

    /// Morrow's ids, its alarms', their dates' and its timers', fit in 48 bits; AlarmKit's are UUIDs. This prefix marks
    /// Morrow's. Timers have the top bit set (see TimerAlarmIds.kt), so they never clash with the others.
    private static let prefix = "4D4F5252-4F57-4000-8000-"

    static func uuid(for id: Int64) -> UUID {
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

    // AlarmKit draws its own Stop from iOS 26.1; this one shows on 26.0.
    private static let stopButton = AlarmButton(text: "Stop", textColor: .white, systemImageName: "stop.fill")

    /// Pip's sun, #F5A65B (Illustration.sun), the Ring screen's accent, for the alarm's buttons.
    private static let sunrise = Color(red: 245 / 255, green: 166 / 255, blue: 91 / 255)
}

/// How one hand-over went. Set by the queued work and read once it's done, in the same task, so never at once.
private final class HandOver {
    var refused = false
}
