import Foundation

/// Launches the `vphone-cli` VM process with the automation API enabled and a
/// fixed token so the Android client can authenticate.
final class VPhoneRunner {
    private let config: Config
    private var process: Process?
    private var didPrintAPIInfo = false
    private var stopping = false
    private var outputBuffer = ""

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

        // `vphone-cli vm launch` boots the VM and may then exit while the
        // vphone-vm process keeps running detached, so its exit is not an error.
        process.terminationHandler = { [weak self] proc in
            guard let self else { return }
            if self.stopping {
                Log.info("vphone launch process stopped.")
            } else {
                Log.info("vphone-cli exited (status \(proc.terminationStatus)); the VM keeps running.")
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
        outputBuffer += text
        let lines = outputBuffer.components(separatedBy: "\n")
        outputBuffer = lines.last ?? ""
        for line in lines.dropLast() {
            handleLine(line)
        }
    }

    private func handleLine(_ line: String) {
        let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty {
            Log.info("vphone: \(trimmed)")
        }
        if !didPrintAPIInfo, line.contains("[api]") {
            didPrintAPIInfo = true
            Log.info("Automation API is up on 127.0.0.1:\(config.port).")
        }
    }

    func stop() {
        stopping = true
        // Stop the VM (vphone-vm) explicitly, since vphone-cli may have detached.
        let stop = Process()
        stop.executableURL = URL(fileURLWithPath: "/usr/bin/env")
        stop.arguments = [config.vphonePath, "vm", "stop", config.vm]
        do {
            try stop.run()
            stop.waitUntilExit()
        } catch {
            Log.error("Failed to stop VM: \(error.localizedDescription)")
        }
        if let process, process.isRunning {
            process.terminate()
        }
    }
}
