// swift-tools-version:5.7
import PackageDescription

// swift-tools-version:5.7
import PackageDescription

let package = Package(
    name: "SDJWTSwift",
    platforms: [
        .iOS(.v13),
        .macOS(.v12)
    ],
    products: [
        .library(
            name: "SDJWTSwift",
            type: .dynamic,
            targets: ["SDJWTSwift"]
        ),
    ],
    targets: [
        .target(
            name: "SDJWTSwift",
            dependencies: ["sdjwtwrapperFFI"],
            path: "wrapper/output-frameworks/sd-jwt-swift/SDJWTSwift/Sources/Swift"
        ),
        .target(
            name: "sdjwtwrapperFFI",
            dependencies: ["libsdjwt"],
            path: "./wrapper/output-frameworks/sd-jwt-swift/SDJWTSwift/Sources/C"),
        // LOCAL
//       .binaryTarget(
//           name: "libsdjwt",
//           path: "./wrapper/output-frameworks/sd-jwt-swift/libsdjwt.xcframework.zip"
//       )
        // RELEASE
        .binaryTarget(
            name: "libsdjwt",
            url: "https://github.com/input-output-hk/sd-jwt-rust/releases/download/0.1.0/libsdjwt.xcframework.zip",
            checksum: "0bfaba65bb14b3023a4496c104097d15f022ca2fc30000979d91b6e0b15cca46"
        )
    ]
)
