import SwiftUI
import sharedKit

@main struct IosApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {
    var body: some View {
        ComposeView().ignoresSafeArea()
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let driverFactory = DriverFactory()
        let controller = MainKt.MainViewController(driverFactory: driverFactory)
        return controller
    }

    func updateUIViewController(_: UIViewController, context: Context) {
    }
}
