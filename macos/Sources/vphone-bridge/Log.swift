import Foundation

enum Log {
    static let prefix = "[vphone-bridge]"

    static func info(_ message: String) {
        print("\(prefix) \(message)")
        fflush(stdout)
    }

    static func error(_ message: String) {
        fputs("\(prefix) \(message)\n", stderr)
        fflush(stderr)
    }
}
