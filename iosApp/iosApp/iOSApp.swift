import SwiftUI
import MavlinkJoystick

class AppDelegate: NSObject, UIApplicationDelegate {
    static var orientationLock = UIInterfaceOrientationMask.landscape

    func application(_ application: UIApplication, supportedInterfaceOrientationsFor window: UIWindow?) -> UIInterfaceOrientationMask {
        return AppDelegate.orientationLock
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    
    init() {
        OrientationManager.shared.onOrientationChangeRequested = { orientation in
            let targetMask: UIInterfaceOrientationMask
            let preferredOrientation: UIInterfaceOrientationMask
            
            switch orientation {
            case .landscape:
                targetMask = .landscape
                preferredOrientation = .landscape
            case .portrait:
                targetMask = .portrait
                preferredOrientation = .portrait
            case .all:
                targetMask = .allButUpsideDown
                preferredOrientation = [] // Let system decide or pick landscape as default
            default:
                targetMask = .landscape
                preferredOrientation = .landscape
            }
            
            AppDelegate.orientationLock = targetMask
            
            // Force update on the main thread
            DispatchQueue.main.async {
                if #available(iOS 16.0, *) {
                    let scenes = UIApplication.shared.connectedScenes
                    let windowScene = scenes.first { $0.activationState == .foregroundActive } as? UIWindowScene
                        ?? scenes.first as? UIWindowScene
                    
                    let geometryUpdate = UIWindowScene.GeometryPreferences.iOS(interfaceOrientations: preferredOrientation.isEmpty ? targetMask : preferredOrientation)
                    windowScene?.requestGeometryUpdate(geometryUpdate) { error in
                        print("Failed to update orientation: \(error.localizedDescription)")
                    }
                    
                    // Also notify the view controller to update its supported orientations
                    windowScene?.keyWindow?.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations()
                } else {
                    let value: Int
                    if targetMask == .portrait {
                        value = UIInterfaceOrientation.portrait.rawValue
                    } else {
                        value = UIInterfaceOrientation.landscapeLeft.rawValue
                    }
                    UIDevice.current.setValue(value, forKey: "orientation")
                    UIViewController.attemptRotationToDeviceOrientation()
                }
            }
        }
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
