import Foundation
import Dispatch

/// Keeps `adb reverse tcp:<port> tcp:<port>` in place so the phone can reach
/// the vphone automation API on the Mac through its own loopback.
final class AdbTunnel {
    private let config: Config
    private var knownSerials = Set<String>()
    private var running = false

    init(config: Config) {
        self.config = config
    }

    func start() {
        running = true
        DispatchQueue.global(qos: .utility).async { [weak self] in
            self?.pollLoop()
        }
    }

    func stop() {
        running = false
        removeAllReverses()
    }

    private func pollLoop() {
        while running {
            refresh()
            Thread.sleep(forTimeInterval: config.pollInterval)
        }
    }

    private func refresh() {
        let devices = listDevices()
        if devices.isEmpty {
            if !knownSerials.isEmpty {
                knownSerials.removeAll()
                Log.info("No Android device connected. Waiting for USB…")
            }
            return
        }

        let newlyAttached = devices.subtracting(knownSerials)
        if !newlyAttached.isEmpty {
            Log.info("Device attached: \(newlyAttached.sorted().joined(separator: ", "))")
        }

        // Re-apply the reverse tunnel for every device in `device` state. It is
        // idempotent and must be re-applied after a USB reconnect, since adbd
        // forgets reverse forwards when the transport drops.
        for serial in devices {
            runReverse(serial: serial)
        }
        knownSerials = devices
    }

    /// Returns the serials currently reported with state `device`.
    private func listDevices() -> Set<String> {
        guard let output = runAdb(["devices"]),
              output.contains("List of devices attached")
        else { return [] }

        var result = Set<String>()
        let lines = output.split(separator: "\n").map(String.init)
        for line in lines.dropFirst() {
            let parts = line.split(separator: "\t", omittingEmptySubsequences: false).map { $0.trimmingCharacters(in: .whitespaces) }
            guard parts.count >= 2, parts[1] == "device", !parts[0].isEmpty else { continue }
            result.insert(parts[0])
        }
        return result
    }

    private func runReverse(serial: String) {
        let reverse = "tcp:\(config.port)"
        _ = runAdb(["-s", serial, "reverse", reverse, reverse])
    }

    private func removeAllReverses() {
        for serial in knownSerials {
            _ = runAdb(["-s", serial, "reverse", "--remove", "tcp:\(config.port)"])
        }
    }

    @discardableResult
    private func runAdb(_ arguments: [String]) -> String? {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/env")
        process.arguments = [config.adbPath] + arguments
        let outPipe = Pipe()
        let errPipe = Pipe()
        process.standardOutput = outPipe
        process.standardError = errPipe
        do {
            try process.run()
            process.waitUntilExit()
            let outData = outPipe.fileHandleForReading.readDataToEndOfFile()
            let errData = errPipe.fileHandleForReading.readDataToEndOfFile()
            if process.terminationStatus != 0 {
                let err = String(data: errData, encoding: .utf8) ?? ""
                let trimmed = err.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty {
                    Log.error("adb \(arguments.joined(separator: " ")) failed: \(trimmed)")
                }
            }
            return String(data: outData, encoding: .utf8)
        } catch {
            Log.error("Failed to run adb: \(error.localizedDescription)")
            return nil
        }
    }
}
