import SwiftUI
import Shared

@main
struct BastionZeroApp: App {
    init() {
        // BGTaskScheduler requires registration before launch completes.
        IosPowerKt.registerBackgroundTasks()
    }

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.all)
                .preferredColorScheme(.dark)
        }
    }
}
