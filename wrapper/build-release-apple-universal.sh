#!/bin/bash -e

echo "Verify and install required targets"
targets=(
  "x86_64-apple-ios"
  "aarch64-apple-ios"
  "aarch64-apple-ios-sim"
  "aarch64-apple-darwin"
  "x86_64-apple-darwin"
)

for target in "${targets[@]}"; do
  if ! rustup target list | grep -q "$target (installed)"; then
    echo "Target $target is not installed. Installing..."
    rustup target add "$target"
    echo "Target $target installed."
  else
    echo "Target $target is already installed."
  fi
done

# Create output directories
mkdir -p ./target/universal-ios-sim/release || true
mkdir -p ./target/universal-darwin/release || true

# Generate Uniffi bindings
echo "Creating uniffi bindings"
cargo run --bin uniffi-bindgen generate src/sdjwtwrapper.udl --language swift -o ./wrappers/swift/sd-jwt

# Build targets
echo "Build all targets"
for target in "${targets[@]}"; do
  echo "Starting $target build"
  cargo build --release --target "$target"
  echo "Finished $target build"
done

# Remove existing files in the destination directories
rm -f ./target/universal-ios-sim/release/libsdjwtwrapper.a || true
rm -f ./target/universal-darwin/release/libsdjwtwrapper.a || true
rm -dr ./target/universal/libsdjwt.xcframework || true

# Create universal libraries
echo "Creating lipo universal libraries"
lipo -create ./target/aarch64-apple-ios-sim/release/libsdjwtwrapper.a ./target/x86_64-apple-ios/release/libsdjwtwrapper.a -output ./target/universal-ios-sim/release/libsdjwtwrapper.a

lipo -create ./target/aarch64-apple-darwin/release/libsdjwtwrapper.a ./target/x86_64-apple-darwin/release/libsdjwtwrapper.a -output ./target/universal-darwin/release/libsdjwtwrapper.a

# Create XCFramework
echo "Creating xcframework"
xcodebuild -create-xcframework \
  -library ./target/aarch64-apple-ios/release/libsdjwtwrapper.a \
  -headers ./wrappers/swift/sd-jwt/ \
  -library ./target/universal-ios-sim/release/libsdjwtwrapper.a \
  -headers ./wrappers/swift/sd-jwt/ \
  -library ./target/universal-darwin/release/libsdjwtwrapper.a \
  -headers ./wrappers/swift/sd-jwt/ \
  -output ./target/universal/libsdjwt.xcframework

echo "Removing .swift files from headers"
dir="./target/universal/libsdjwt.xcframework"

# Compress and copy XCFramework
target_dir_name="libsdjwt.xcframework"
source_dir="./target/universal"
dest_dir="./output-frameworks/sd-jwt-swift"
zip_name="libsdjwt.xcframework.zip"

echo "Zip xcframework"

# Create destination directory if it doesn't exist
mkdir -p "$dest_dir"

# Remove any existing zip file
rm -f "$dest_dir/$zip_name" || true

# Compress the XCFramework
(cd $source_dir && zip -r "../../$dest_dir/$zip_name" $target_dir_name)

echo "Copy .swift binders"
rm -f "./output-frameworks/sd-jwt-swift/SDJWTSwift/Sources/Swift/sdjwtwrapper.swift" || true
mkdir -p ./output-frameworks/sd-jwt-swift/SDJWTSwift/Sources/Swift || true
mv "./wrappers/swift/sd-jwt/sdjwtwrapper.swift" "./output-frameworks/sd-jwt-swift/SDJWTSwift/Sources/Swift/sdjwtwrapper.swift"

rm -f "/wrappers/swift/sd-jwt/sdjwtwrapper.swift" || true

