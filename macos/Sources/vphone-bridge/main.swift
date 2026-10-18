import Foundation
import Dispatch
import Darwin

let config = Config.parse(arguments: Array(CommandLine.arguments.dropFirst()))

Log.info("vphone-bridge starting")
Log.info("VM: \(config.vm) | port: \(config.port) | token: \(config.token)")

let runner = VPhoneRunner(config: config)
let tunnel = AdbTunnel(config: config)

if config.launchVPhone {
    runner.launch()
} else {
    Log.info("Not launching vphone (--no-launch).")
    Log.info("Assuming it is already running with --api-listen 127.0.0.1:\(config.port).")
}

tunnel.start()

// Clean shutdown on Ctrl-C / SIGTERM.
signal(SIGINT, SIG_IGN)
signal(SIGTERM, SIG_IGN)
let sigintSource = DispatchSource.makeSignalSource(signal: SIGINT, queue: .main)
let sigtermSource = DispatchSource.makeSignalSource(signal: SIGTERM, queue: .main)

var shuttingDown = false
func shutdown() {
    guard !shuttingDown else { return }
    shuttingDown = true
    Log.info("Shutting down…")
    tunnel.stop()
    runner.stop()
    exit(0)
}

sigintSource.setEventHandler { shutdown() }
sigtermSource.setEventHandler { shutdown() }
sigintSource.resume()
sigtermSource.resume()

dispatchMain()
