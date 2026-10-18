// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "vphone-bridge",
    platforms: [.macOS(.v13)],
    targets: [
        .executableTarget(
            name: "vphone-bridge",
            path: "Sources/vphone-bridge"
        )
    ]
)
