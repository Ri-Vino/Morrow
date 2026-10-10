import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        IosApp.shared.start(alarmKit: AlarmKitScheduler())
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea()
        }
    }
}
