#!/bin/bash -e


# Remove existing files in the destination directories
rm -r ./target/x86_64-unknown-linux-gnu || true
rm -f ./output-frameworks/jvm/src/main/uniffi/ || true

# Generate code
# Output the 'target/uniffi/sd_jwt/sd_jwt.kt'
cargo build --release --target x86_64-unknown-linux-gnu

# Generate the file anoncreds.kt in wrappers/kotlin/anoncreds/uniffi/anoncreds/anoncreds.kt
# cargo run --bin uniffi-bindgen generate src/anoncreds.udl --language kotlin -o ./wrappers/kotlin/anoncreds
cargo run --bin uniffi-bindgen generate ./uniffi/sd-jwt.udl \
  --lib-file ./target/x86_64-unknown-linux-gnu/release/libsdjwt.rlib \
  --language kotlin -o ./output-frameworks/jvm/src/main


# # # Move code to output-frameworks/jvm
# # rm -f ./output-frameworks/jvm/src/main/uniffi/anoncreds_wrapper/anoncreds_wrapper.kt
# # mv ./wrappers/kotlin/anoncreds/uniffi/anoncreds_wrapper/anoncreds_wrapper.kt ./output-frameworks/jvm/src/main/uniffi/anoncreds_wrapper/anoncreds_wrapper.kt

# # make the jar
# cd ./output-frameworks/jvm
# ./gradlew jar
# # Output ./output-frameworks/jvm/build/libs/sd-jwt-jvm-1.0-SNAPSHOT.jar


