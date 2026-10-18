import Foundation

/// Launches and supervises the `vphone-cli` process with the automation API
/// enabled, and a fixed token so the Android client can authenticate.
final class VPhoneRunner {
    private let config: Config
    private var process: Process?
    private var didPrintAPIInfo = false
    private var stopping = false

    init(config: Config) {
        self.config = config
    }

    func launch() {
        let process = Process()
        // Resolve `vphone-cli` through PATH without requiring an absolute path.
        process.executableURL = URL(fileURLWithPath: "/usr/bin/env")
        process.arguments = [
            config.vphonePath, "vm", "launch", config.vm,
            "--api-listen", "127.0.0.1:\(config.port)",
        ]

        var environment = ProcessInfo.processInfo.environment
        environment["VPHONE_API_TOKEN"] = config.token
        process.environment = environment

        let pipe = Pipe()
        process.standardOutput = pipe
        process.standardError = pipe

        pipe.fileHandleForReading.readabilityHandler = { [weak self] handle in
            let data = handle.availableData
            guard !data.isEmpty else { return }
            if let text = String(data: data, encoding: .utf8), !text.isEmpty {
                self?.consumeOutput(text)
            }
        }

        process.terminationHandler = { [weak self] proc in
            guard let self else { return }
            if self.stopping {
                Log.info("vphone stopped.")
            } else {
                Log.error("vphone exited unexpectedly with status \(proc.terminationStatus).")
                exit(1)
            }
        }

        do {
            try process.run()
            self.process = process
            Log.info("Launched: \(config.vphonePath) vm launch \(config.vm) --api-listen 127.0.0.1:\(config.port)")
            Log.info("Using fixed API token: \(config.token)")
        } catch {
            Log.error("Failed to launch vphone: \(error.localizedDescription)")
            Log.error("Is \(config.vphonePath) installed and on PATH? See --vphone to point at it.")
            exit(1)
        }
    }

    private func consumeOutput(_ text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty {
            Log.info("vphone: \(trimmed)")
        }
        if !didPrintAPIInfo, text.contains("[api]") {
            didPrintAPIInfo = true
            Log.info("Automation API is up on 127.0.0.1:\(config.port).")
            Log.info("The Android app reaches it through `adb reverse` (connect to 127.0.0.1:\(config.port) on the phone).")
        }
    }

    func stop() {
        stopping = true
        if let process, process.isRunning {
            process.terminate()
        }
    }
}
