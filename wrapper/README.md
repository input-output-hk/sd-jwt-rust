# UniFFI Wrapper - KMP

Please install the following in the same order for `macOS`:

- Android Studio (so that all the android compiler and needed linker exist in an easy way)
- Rust: curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
- Cargo Targets:
  - armv7-linux-androideabi: rustup target add armv7-linux-androideabi
  - i686-linux-android: rustup target add i686-linux-android
  - aarch64-linux-android: rustup target add aarch64-linux-android
  - x86_64-linux-android: rustup target add x86_64-linux-android
  - aarch64-apple-darwin: rustup target add aarch64-apple-darwin
  - x86_64-apple-darwin: rustup target add x86_64-apple-darwin
  - aarch64-unknown-linux-gnu: rustup target add aarch64-unknown-linux-gnu
  - x86_64-unknown-linux-gnu: rustup target add x86_64-unknown-linux-gnu
- HomeBrew: /bin/bash -c \"\$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\"
- MacToolChain: brew tap messense/macos-cross-toolchains
- x86-64 LinuxGNU: brew install x86_64-unknown-linux-gnu
- Arch64 LinuxGNU: brew install aarch64-unknown-linux-gnu
- Cargo NDK: cargo install cargo-ndk

```shell
#!/bin/bash

# Install Rust
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh

# Install Cargo Targets
rustup target add armv7-linux-androideabi
rustup target add i686-linux-android
rustup target add aarch64-linux-android
rustup target add x86_64-linux-android
rustup target add aarch64-apple-darwin
rustup target add x86_64-apple-darwin
rustup target add aarch64-unknown-linux-gnu
rustup target add x86_64-unknown-linux-gnu

# Install HomeBrew
/bin/bash -c \"\$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\"

# Install Mac ToolChain
brew tap messense/macos-cross-toolchains

# Install LinuxGNU x86-64
brew install x86_64-unknown-linux-gnu

# Install LinuxGNU Arch64
brew install aarch64-unknown-linux-gnu

# Install Cargo NDK
cargo install cargo-ndk
```
