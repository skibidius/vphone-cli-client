import Foundation

struct Config {
    var vm: String = "iphone"
    var port: Int = 8765
    var token: String = Config.defaultToken
    var vphonePath: String = "vphone-cli"
    var adbPath: String = "adb"
    var launchVPhone: Bool = true
    var pollInterval: Double = 2.0

    /// Shared default token. Must match the Android app's default, and the
    /// vphone `VPHONE_API_TOKEN` rules (16-256 chars of A-Z a-z 0-9 - . _ ~).
    static let defaultToken = "vphone-android-bridge"

    static func parse(arguments: [String]) -> Config {
        var config = Config()
        var args = arguments

        func takeValue() -> String? {
            guard !args.isEmpty else { return nil }
            return args.removeFirst()
        }

        while !args.isEmpty {
            let arg = args.removeFirst()
            switch arg {
            case "--vm": if let v = takeValue() { config.vm = v }
            case "--port": if let v = takeValue(), let p = Int(v) { config.port = p }
            case "--token": if let v = takeValue() { config.token = v }
            case "--vphone": if let v = takeValue() { config.vphonePath = v }
            case "--adb": if let v = takeValue() { config.adbPath = v }
            case "--no-launch": config.launchVPhone = false
            case "--poll": if let v = takeValue(), let d = Double(v) { config.pollInterval = d }
            case "--config": if let v = takeValue() { config = loadJSON(path: v, fallback: config) }
            case "-h", "--help":
                printHelp()
                exit(0)
            default:
                Log.error("Unknown argument: \(arg)")
                printHelp()
                exit(1)
            }
        }
        return config
    }

    static func loadJSON(path: String, fallback: Config) -> Config {
        var config = fallback
        guard let data = try? Data(contentsOf: URL(fileURLWithPath: path)),
              let obj = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
        else {
            Log.error("Could not read config file \(path); using defaults")
            return config
        }
        config.vm = obj["vm"] as? String ?? config.vm
        config.port = obj["port"] as? Int ?? config.port
        config.token = obj["token"] as? String ?? config.token
        config.vphonePath = obj["vphone"] as? String ?? config.vphonePath
        config.adbPath = obj["adb"] as? String ?? config.adbPath
        config.launchVPhone = obj["launch_vphone"] as? Bool ?? config.launchVPhone
        config.pollInterval = obj["poll_interval"] as? Double ?? config.pollInterval
        return config
    }

    static func printHelp() {
        print("""
        vphone-bridge — run vphone-cli and stream it to an Android device over USB (adb reverse).

        Usage:
          vphone-bridge [options]

        Options:
          --vm <name>       vphone VM name (default: iphone)
          --port <n>        API port (default: 8765)
          --token <str>     Fixed API token (default: \(defaultToken))
          --vphone <path>   Path to the vphone-cli binary (default: vphone-cli on PATH)
          --adb <path>      Path to adb (default: adb on PATH)
          --no-launch       Do not launch vphone; only manage adb reverse
          --poll <seconds>  adb device poll interval (default: 2)
          --config <path>   JSON config file with the same keys
          -h, --help        Show this help
        """)
    }
}
