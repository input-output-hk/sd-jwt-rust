import com.android.build.gradle.tasks.ProcessAndroidResources
import com.android.build.gradle.tasks.SourceJarTask
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask
import java.net.URL
import java.util.Base64

val publishedMavenId: String = "io.iohk"
val os: OperatingSystem = OperatingSystem.current()
val rustWrapperDir: File = File(rootDir.parent).resolve("wrapper")

plugins {
    kotlin("multiplatform") version "1.9.23"
    id("com.android.library") version "8.1.4"
    id("org.jetbrains.dokka") version "1.9.20"
    id("org.jlleitschuh.gradle.ktlint") version "11.6.0"
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0-rc-1"
    id("maven-publish")
    id("signing")
}
apply(plugin = "kotlinx-atomicfu")

group = publishedMavenId
version = "1.0-SNAPSHOT"

buildscript {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.23")
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:0.23.1")
        classpath("org.jetbrains.dokka:dokka-base:1.9.20")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
    google()
    maven { url = uri("https://jitpack.io") }
}

@Throws(GradleException::class)
fun getNDKOSVariant(): String {
    return if (os.isMacOsX) {
        "darwin-x86_64"
    } else if (os.isLinux) {
        "linux-x86_64"
    } else {
        // It would be windows-x86_64, but we don't support Windows environment
        throw GradleException("Unsported OS: ${os.name}")
    }
}

val minAndroidVersion: Int = 21
val ANDROID_SDK = System.getenv("ANDROID_HOME")
val NDK = System.getenv("ANDROID_NDK_HOME")
val TOOLCHAIN = "$NDK/toolchains/llvm/prebuilt/${getNDKOSVariant()}"
val AR = "$TOOLCHAIN/bin/llvm-ar"
val CC = "$TOOLCHAIN/bin/aarch64-linux-android$minAndroidVersion-clang"
val CXX = "$TOOLCHAIN/bin/aarch64-linux-android$minAndroidVersion-clang++"
val LD = "$TOOLCHAIN/bin/ld"
val RANLIB = "$TOOLCHAIN/bin/llvm-ranlib"
val STRIP = "$TOOLCHAIN/bin/llvm-strip"
val CARGO_TARGET_X86_64_UNKNOWN_LINUX_GNU_LINKER = "x86_64-linux-gnu-gcc"
val CARGO_TARGET_AARCH64_UNKNOWN_LINUX_GNU_LINKER = "aarch64-linux-gnu-gcc"

// Tasks Declaration

// COMPILING

val buildWrapperForMacOSArch64 by tasks.register<Exec>("buildWrapperForMacOSArch64") {
    group = "rust-compiling"
    description = "Build and compile Wrapper for macOS aarch64"
    workingDir = rustWrapperDir
    inputs.files(fileTree(rustWrapperDir.resolve("src")))
    outputs.files(fileTree(rustWrapperDir.resolve("target").resolve("aarch64-apple-darwin")))
    commandLine("cargo", "build", "--release", "--target", "aarch64-apple-darwin", "--target-dir", "${rustWrapperDir.resolve("target")}")

    onlyIf {
        !rustWrapperDir.resolve("target/aarch64-apple-darwin").exists()
    }
}

val buildWrapperForMacOSX8664 by tasks.register<Exec>("buildWrapperForMacOSX86_64") {
    group = "rust-compiling"
    description = "Build and compile Wrapper for macOS x86_64"
    workingDir = rustWrapperDir
    inputs.files(fileTree(rustWrapperDir.resolve("src")))
    outputs.files(fileTree(rustWrapperDir.resolve("target").resolve("x86_64-apple-darwin")))
    commandLine("cargo", "build", "--release", "--target", "x86_64-apple-darwin", "--target-dir", "${rustWrapperDir.resolve("target")}")

    onlyIf {
        !rustWrapperDir.resolve("target/x86_64-apple-darwin").exists()
    }
}

val buildWrapperForMacOSUniversal by tasks.register("buildWrapperForMacOSUniversal") {
    group = "rust-compiling"
    description = "Build and compile Wrapper for macOS"
    dependsOn(buildWrapperForMacOSArch64, buildWrapperForMacOSX8664)
}

val buildWrapperForLinuxX8664 by tasks.register<Exec>("buildWrapperForLinuxX86_64") {
    group = "rust-compiling"
    description = "Build and compile Wrapper for Linux x86_64"
    workingDir = rustWrapperDir
    if (os.isMacOsX) {
        val localEnv = this.environment
        localEnv += mapOf(
            "PATH" to "${environment["PATH"]}:$CARGO_TARGET_X86_64_UNKNOWN_LINUX_GNU_LINKER",
            "CARGO_TARGET_X86_64_UNKNOWN_LINUX_GNU_LINKER" to CARGO_TARGET_X86_64_UNKNOWN_LINUX_GNU_LINKER
        )
        this.environment = localEnv
    }
    inputs.files(fileTree(rustWrapperDir.resolve("src")))
    outputs.files(fileTree(rustWrapperDir.resolve("target").resolve("x86_64-unknown-linux-gnu")))

    commandLine("cargo", "build", "--release", "--target", "x86_64-unknown-linux-gnu", "--target-dir", "${rustWrapperDir.resolve("target")}")

    onlyIf {
        !rustWrapperDir.resolve("target/x86_64-unknown-linux-gnu").exists()
    }
}

val buildWrapperForLinuxArch64 by tasks.register<Exec>("buildWrapperForLinuxArch64") {
    group = "rust-compiling"
    description = "Build and compile Wrapper for Linux aarch64"
    workingDir = rustWrapperDir
    if (os.isMacOsX) {
        val localEnv = this.environment
        localEnv += mapOf(
            "PATH" to "${environment["PATH"]}:$CARGO_TARGET_AARCH64_UNKNOWN_LINUX_GNU_LINKER",
            "CARGO_TARGET_AARCH64_UNKNOWN_LINUX_GNU_LINKER" to CARGO_TARGET_AARCH64_UNKNOWN_LINUX_GNU_LINKER
        )
        this.environment = localEnv
    }
    inputs.files(fileTree(rustWrapperDir.resolve("src")))
    outputs.files(fileTree(rustWrapperDir.resolve("target").resolve("aarch64-unknown-linux-gnu")))
    commandLine("cargo", "build", "--release", "--target", "aarch64-unknown-linux-gnu", "--target-dir", "${rustWrapperDir.resolve("target")}")

    onlyIf {
        !rustWrapperDir.resolve("target/aarch64-unknown-linux-gnu").exists()
    }
}

val buildWrapperForLinuxUniversal by tasks.register("buildWrapperForLinuxUniversal") {
    group = "rust-compiling"
    description = "Build and compile Wrapper for Linux"
    dependsOn(buildWrapperForLinuxArch64, buildWrapperForLinuxX8664)
}

val buildWrapperForAndroidX8664 by tasks.register<Exec>("buildWrapperForAndroidX86_64") {
    group = "rust-compiling"
    description = "Build and compile Wrapper for Android X86_64"
    workingDir = rustWrapperDir
    val localEnv = this.environment
    localEnv += mapOf(
        "PATH" to "${environment["PATH"]}:$TOOLCHAIN:$AR:$CC:$CXX:$LD:$RANLIB:$STRIP:$TOOLCHAIN/bin/",
        "ANDROID_SDK" to ANDROID_SDK,
        "NDK" to NDK,
        "TOOLCHAIN" to TOOLCHAIN,
        "AR" to AR,
        "CC" to CC,
        "CXX" to CXX,
        "LD" to LD,
        "RANLIB" to RANLIB,
        "STRIP" to STRIP
    )
    this.environment = localEnv
    inputs.files(fileTree(rustWrapperDir.resolve("src")))
    outputs.files(fileTree(rustWrapperDir.resolve("target").resolve("x86_64-linux-android")))
    commandLine("cargo", "ndk", "build", "--release", "--target", "x86_64-linux-android", "--target-dir", "${rustWrapperDir.resolve("target")}")

    onlyIf {
        !rustWrapperDir.resolve("target/x86_64-linux-android").exists()
    }
}

val buildWrapperForAndroidArch64 by tasks.register<Exec>("buildWrapperForAndroidArch64") {
    group = "rust-compiling"
    description = "Build and compile Wrapper for Android arch64"
    workingDir = rustWrapperDir
    val localEnv = this.environment
    localEnv += mapOf(
        "PATH" to "${environment["PATH"]}:$TOOLCHAIN:$AR:$CC:$CXX:$LD:$RANLIB:$STRIP:$TOOLCHAIN/bin/",
        "ANDROID_SDK" to ANDROID_SDK,
        "NDK" to NDK,
        "TOOLCHAIN" to TOOLCHAIN,
        "AR" to AR,
        "CC" to CC,
        "CXX" to CXX,
        "LD" to LD,
        "RANLIB" to RANLIB,
        "STRIP" to STRIP
    )
    this.environment = localEnv
    inputs.files(fileTree(rustWrapperDir.resolve("src")))
    outputs.files(fileTree(rustWrapperDir.resolve("target").resolve("aarch64-linux-android")))
    commandLine("cargo", "ndk", "build", "--release", "--target", "aarch64-linux-android", "--target-dir", "${rustWrapperDir.resolve("target")}")

    onlyIf {
        !rustWrapperDir.resolve("target/aarch64-linux-android").exists()
    }
}

val buildWrapperForAndroidI686 by tasks.register<Exec>("buildWrapperForAndroidI686") {
    group = "rust-compiling"
    description = "Build and compile Wrapper for Android I686"
    workingDir = rustWrapperDir
    val localEnv = this.environment
    localEnv += mapOf(
        "PATH" to "${environment["PATH"]}:$TOOLCHAIN:$AR:$CC:$CXX:$LD:$RANLIB:$STRIP:$TOOLCHAIN/bin/",
        "ANDROID_SDK" to ANDROID_SDK,
        "NDK" to NDK,
        "TOOLCHAIN" to TOOLCHAIN,
        "AR" to AR,
        "CC" to CC,
        "CXX" to CXX,
        "LD" to LD,
        "RANLIB" to RANLIB,
        "STRIP" to STRIP
    )
    this.environment = localEnv
    inputs.files(fileTree(rustWrapperDir.resolve("src")))
    outputs.files(fileTree(rustWrapperDir.resolve("target").resolve("i686-linux-android")))
    commandLine("cargo", "ndk", "build", "--release", "--target", "i686-linux-android", "--target-dir", "${rustWrapperDir.resolve("target")}")

    onlyIf {
        !rustWrapperDir.resolve("target/i686-linux-android").exists()
    }
}

val buildWrapperForAndroidArmv7a by tasks.register<Exec>("buildWrapperForAndroidArmv7a") {
    group = "rust-compiling"
    description = "Build and compile Wrapper for Android Armv7a"
    workingDir = rustWrapperDir
    val localEnv = this.environment
    localEnv += mapOf(
        "PATH" to "${environment["PATH"]}:$TOOLCHAIN:$AR:$CC:$CXX:$LD:$RANLIB:$STRIP:$TOOLCHAIN/bin/",
        "ANDROID_SDK" to ANDROID_SDK,
        "NDK" to NDK,
        "TOOLCHAIN" to TOOLCHAIN,
        "AR" to AR,
        "CC" to CC,
        "CXX" to CXX,
        "LD" to LD,
        "RANLIB" to RANLIB,
        "STRIP" to STRIP
    )
    this.environment = localEnv
    inputs.files(fileTree(rustWrapperDir.resolve("src")))
    outputs.files(fileTree(rustWrapperDir.resolve("target").resolve("armv7-linux-androideabi")))
    commandLine("cargo", "ndk", "build", "--release", "--target", "armv7-linux-androideabi", "--target-dir", "${rustWrapperDir.resolve("target")}")

    onlyIf {
        !rustWrapperDir.resolve("target/armv7-linux-androideabi").exists()
    }
}

val buildWrapperForAndroidUniversal by tasks.register("buildWrapperForAndroidUniversal") {
    group = "rust-compiling"
    description = "Build and compile Wrapper for Android"
    dependsOn(buildWrapperForAndroidX8664, buildWrapperForAndroidArch64, buildWrapperForAndroidI686, buildWrapperForAndroidArmv7a)
}

val buildWrapper by tasks.register("buildWrapper") {
    group = "rust-compiling"
    description = "Build and compile Wrapper"
    dependsOn(buildWrapperForMacOSUniversal, buildWrapperForLinuxUniversal, buildWrapperForAndroidUniversal)
}

// COPY BINARY

val copyGeneratedBinaryForMacOSX8664 by tasks.register<Copy>("copyGeneratedBinaryForMacOSX86_64") {
    group = "rust-compiling"
    description = "Copy all generated macOS x86_64 binaries to generated resources folder"
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    include("*.so", "*.a", "*.d", "*.dylib")
    from(rustWrapperDir.resolve("target").resolve("x86_64-apple-darwin").resolve("release"))
    into(projectDir.resolve("build").resolve("generatedResources").resolve("jvm").resolve("main").resolve("darwin-x86-64"))
    dependsOn(buildWrapperForMacOSX8664)
}

val copyGeneratedBinaryForMacOSArch64 by tasks.register<Copy>("copyGeneratedBinaryForMacOSArch64") {
    group = "rust-compiling"
    description = "Copy all generated macOS aarch64 binaries to generated resources folder"
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    include("*.so", "*.a", "*.d", "*.dylib")
    from(rustWrapperDir.resolve("target").resolve("aarch64-apple-darwin").resolve("release"))
    into(projectDir.resolve("build").resolve("generatedResources").resolve("jvm").resolve("main").resolve("darwin-aarch64"))
    dependsOn(buildWrapperForMacOSArch64)
}

val copyGeneratedBinaryForMacOS by tasks.register("copyGeneratedBinaryForMacOS") {
    group = "rust-compiling"
    description = "Copy all generated macOS binaries to generated resources folder"
    dependsOn(copyGeneratedBinaryForMacOSX8664, copyGeneratedBinaryForMacOSArch64)
}

val copyGeneratedBinaryForLinuxX8664 by tasks.register<Copy>("copyGeneratedBinaryForLinuxX86_64") {
    group = "rust-compiling"
    description = "Copy all generated Linux x86_64 binaries to generated resources folder"
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    include("*.so", "*.a", "*.d", "*.dylib")
    from(rustWrapperDir.resolve("target").resolve("x86_64-unknown-linux-gnu").resolve("release"))
    into(projectDir.resolve("build").resolve("generatedResources").resolve("jvm").resolve("main").resolve("linux-x86-64"))
    dependsOn(buildWrapperForLinuxX8664)
}

val copyGeneratedBinaryForLinuxArch64 by tasks.register<Copy>("copyGeneratedBinaryForLinuxArch64") {
    group = "rust-compiling"
    description = "Copy all generated Linux aarch64 binaries to generated resources folder"
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    include("*.so", "*.a", "*.d", "*.dylib")
    from(rustWrapperDir.resolve("target").resolve("aarch64-unknown-linux-gnu").resolve("release"))
    into(projectDir.resolve("build").resolve("generatedResources").resolve("jvm").resolve("main").resolve("linux-aarch64"))
    dependsOn(buildWrapperForLinuxArch64)
}

val copyGeneratedBinaryForLinux by tasks.register("copyGeneratedBinaryForLinux") {
    group = "rust-compiling"
    description = "Copy all generated Linux binaries to generated resources folder"
    dependsOn(copyGeneratedBinaryForLinuxX8664, copyGeneratedBinaryForLinuxArch64)
}

val copyGeneratedBinaryForAndroidX8664 by tasks.register<Copy>("copyGeneratedBinaryForAndroidX86_64") {
    group = "rust-compiling"
    description = "Copy all generated Android X86_64 binaries to generated resources folder"
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    include("*.so", "*.a", "*.d", "*.dylib")
    from(rustWrapperDir.resolve("target").resolve("x86_64-linux-android").resolve("release"))
    into(projectDir.resolve("build").resolve("generatedResources").resolve("android").resolve("main").resolve("jniLibs").resolve("x86_64"))
    dependsOn(buildWrapperForAndroidX8664)
}

val copyGeneratedBinaryForAndroidArch64 by tasks.register<Copy>("copyGeneratedBinaryForAndroidArch64") {
    group = "rust-compiling"
    description = "Copy all generated Android aarch64 binaries to generated resources folder"
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    include("*.so", "*.a", "*.d", "*.dylib")
    from(rustWrapperDir.resolve("target").resolve("aarch64-linux-android").resolve("release"))
    into(projectDir.resolve("build").resolve("generatedResources").resolve("android").resolve("main").resolve("jniLibs").resolve("arm64-v8a"))
    dependsOn(buildWrapperForAndroidArch64)
}

val copyGeneratedBinaryForAndroidI686 by tasks.register<Copy>("copyGeneratedBinaryForAndroidI686") {
    group = "rust-compiling"
    description = "Copy all generated Android i686 binaries to generated resources folder"
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    include("*.so", "*.a", "*.d", "*.dylib")
    from(rustWrapperDir.resolve("target").resolve("i686-linux-android").resolve("release"))
    into(projectDir.resolve("build").resolve("generatedResources").resolve("android").resolve("main").resolve("jniLibs").resolve("x86"))
    dependsOn(buildWrapperForAndroidI686)
}

val copyGeneratedBinaryForAndroidArmv7a by tasks.register<Copy>("copyGeneratedBinaryForAndroidArmv7a") {
    group = "rust-compiling"
    description = "Copy all generated Android armv7a binaries to generated resources folder"
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    include("*.so", "*.a", "*.d", "*.dylib")
    from(rustWrapperDir.resolve("target").resolve("armv7-linux-androideabi").resolve("release"))
    into(projectDir.resolve("build").resolve("generatedResources").resolve("android").resolve("main").resolve("jniLibs").resolve("armeabi-v7a"))
    dependsOn(buildWrapperForAndroidArmv7a)
}

val copyGeneratedBinaryForAndroid by tasks.register("copyGeneratedBinaryForAndroid") {
    group = "rust-compiling"
    description = "Copy all generated Android binaries to generated resources folder"
    dependsOn(copyGeneratedBinaryForAndroidArch64, copyGeneratedBinaryForAndroidX8664, copyGeneratedBinaryForAndroidI686, copyGeneratedBinaryForAndroidArmv7a)
}

val copyGeneratedBinaries by tasks.register("copyGeneratedBinaries") {
    group = "rust-compiling"
    description = "Copy all generated binaries to generated resources folder"
    dependsOn(copyGeneratedBinaryForMacOS, copyGeneratedBinaryForLinux, copyGeneratedBinaryForAndroid)
}

val installCargoBingenKMP by tasks.register<Exec>("installCargoBingenKMP") {
    group = "rust-compiling"
    description = "Install cargo modules"
    workingDir = rustWrapperDir
    commandLine(
        "cargo",
        "install",
        "--bin",
        "uniffi-bindgen-kotlin-multiplatform",
        "uniffi_bindgen_kotlin_multiplatform@0.1.0"
    )
}

val generateBindings by tasks.register<Exec>("generateBindings") {
    group = "rust-compiling"
    description = "Generate code bindings to module"
    workingDir = rustWrapperDir
    commandLine(
        "uniffi-bindgen-kotlin-multiplatform",
        "--lib-file",
        "./target/x86_64-unknown-linux-gnu/release/libsdjwtwrapper.rlib",
        "--out-dir",
        "../sd-jwt-kmp/build/generated",
        "--crate",
        "sdjwtwrapper",
        "./src/sdjwtwrapper.udl"
    )
    dependsOn(installCargoBingenKMP, buildWrapperForLinuxX8664)
}

/**
 * Copy generated bindings to the module
 */
val copyBindings by tasks.register<Copy>("copyBindings") {
    group = "rust-compiling"
    description = "Copy generated bindings to module"
    from(project.layout.buildDirectory.asFile.get().resolve("generated").resolve("jvmMain"))
    into(project.layout.buildDirectory.asFile.get().resolve("generated").resolve("androidMain"))
    dependsOn(generateBindings)
    mustRunAfter(copyGeneratedBinaries)
}

/**
 * The main build Rust lib task. It will do the following:
 * - Build the lib
 * - Generate the bindings
 * - Move the generated bindings to the lib module to be used in Kotlin KMM
 */
val buildRust by tasks.register("buildRust") {
    group = "rust"
    description = """
        The main build Rust lib task. It will do the following:
        - Build the lib
        - Generate the bindings
        - Move the generated bindings to the lib module to be used in Kotlin KMM
    """.trimIndent()
    dependsOn(copyBindings, copyGeneratedBinaries)
}

/**
 * Generate rust documentation
 */
val generateDocumentation by tasks.register<Exec>("rustDoc") {
    group = "documentation"
    description = "Generate rust documentation"
    commandLine("cargo", "doc")
}

/**
 * The `javadocJar` variable is used to register a `Jar` task to generate a Javadoc JAR file.
 * The Javadoc JAR file is created with the classifier "javadoc" and it includes the HTML documentation generated
 * by the `dokkaHtml` task.
 */
val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.dokkaHtml)
}

val projectClean by tasks.register("ProjectClean") {
    group = "rust"
    delete(rustWrapperDir.resolve("target"))
    delete(project.layout.buildDirectory.asFile.get().resolve("generated"))
    delete(project.layout.buildDirectory.asFile.get().resolve("generatedResources"))
    delete(project.layout.buildDirectory.asFile.get().resolve("processedResources"))
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
        withSourcesJar()
        publishing {
            publications {
                withType<MavenPublication> {
                    artifact(javadocJar)
                }
            }
        }
    }
    androidTarget {
        publishAllLibraryVariants()
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }
    applyDefaultHierarchyTemplate()

    sourceSets {
        val generatedCodePath = project.layout.buildDirectory.asFile.get().resolve("generated")
        commonMain {
            kotlin.srcDir(generatedCodePath.resolve("commonMain").resolve("kotlin"))
            dependencies {
                implementation("com.squareup.okio:okio:3.7.0")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        jvmMain {
            kotlin.srcDir(generatedCodePath.resolve("jvmMain").resolve("kotlin"))
            val generatedResources = project.layout.buildDirectory.asFile.get()
                .resolve("generatedResources")
                .resolve("jvm")
                .resolve("main")
            resources.srcDir(generatedResources)
            dependencies {
                implementation("net.java.dev.jna:jna:5.13.0")
            }
        }
        androidMain {
            kotlin.srcDir(generatedCodePath.resolve("androidMain").resolve("kotlin"))
            dependencies {
                implementation("net.java.dev.jna:jna:5.13.0@aar")
            }
        }

        all {
            languageSettings {
                optIn("kotlin.RequiresOptIn")
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
            }
        }
    }
}

android {
    ndkVersion = "26.0.10792818"
    compileSdk = 34
    namespace = rootProject.group.toString()

    sourceSets["main"].jniLibs {
        setSrcDirs(
            listOf(
                project.layout.buildDirectory.asFile.get()
                    .resolve("generatedResources")
                    .resolve("android")
                    .resolve("main")
                    .resolve("jniLibs")
            )
        )
    }
    defaultConfig {
        minSdk = 21
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    /**
     * Because Software Components will not be created automatically for Maven publishing from
     * Android Gradle Plugin 8.0. To opt-in to the future behavior, set the Gradle property android.
     * disableAutomaticComponentCreation=true in the `gradle.properties` file or use the new
     * publishing DSL.
     */
    publishing {
        multipleVariants {
            withSourcesJar()
            withJavadocJar()
            allVariants()
        }
    }
}

apply(plugin = "org.jlleitschuh.gradle.ktlint")
ktlint {
    verbose.set(true)
    outputToConsole.set(true)
    filter {
        val generatedCodePath = project.layout.buildDirectory.asFile.get()
            .resolve("build")
            .resolve("generated")

        exclude(
            "$generatedCodePath/*/*",
            "$generatedCodePath/*",
            "$generatedCodePath/**",
            "$generatedCodePath/**/**"
        )
        exclude("**/generated/**")
        exclude("${project.layout.buildDirectory.asFile.get()}/generated/")
        exclude { it.file.path.contains(layout.buildDirectory.dir("generated").get().toString()) }
    }
}

apply(plugin = "org.gradle.maven-publish")
apply(plugin = "org.gradle.signing")
publishing {
    publications {
        withType<MavenPublication> {
            groupId = publishedMavenId
            artifactId = project.name
            artifactId = if (name == "jvm") {
                "${project.name}-$name"
            } else if (name == "kotlinMultiplatform") {
                project.name
            } else {
                name
            }
            version = rootProject.version.toString()
            println("$groupId:$artifactId:$version")
            pom {
                name.set("SD-JWT KMP")
                description.set("SD-JWT KMP Wrapper of Rust Implementation")
                url.set("https://docs.atalaprism.io/")
                organization {
                    name.set("IOG")
                    url.set("https://iohk.io/")
                }
                issueManagement {
                    system.set("Github")
                    url.set("https://github.com/input-output-hk/sd-jwt-rust")
                }
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("hamada147")
                        name.set("Ahmed Moussa")
                        email.set("ahmed.moussa@iohk.io")
                        organization.set("IOG")
                        roles.add("developer")
                        url.set("https://github.com/hamada147")
                    }
                }
                scm {
                    connection.set("scm:git:git://input-output-hk/sd-jwt-rust.git")
                    developerConnection.set("scm:git:ssh://input-output-hk/sd-jwt-rust.git")
                    url.set("https://github.com/input-output-hk/sd-jwt-rust")
                }
            }
            if (System.getenv("BASE64_ARMORED_GPG_SIGNING_KEY_MAVEN") != null) {
                if (System.getenv("BASE64_ARMORED_GPG_SIGNING_KEY_MAVEN").isNotBlank()) {
                    signing {
                        val base64EncodedAsciiArmoredSigningKey: String =
                            System.getenv("BASE64_ARMORED_GPG_SIGNING_KEY_MAVEN") ?: ""
                        val signingKeyPassword: String = System.getenv("SIGNING_KEY_PASSWORD") ?: ""
                        useInMemoryPgpKeys(
                            String(
                                Base64.getDecoder().decode(base64EncodedAsciiArmoredSigningKey.toByteArray())
                            ),
                            signingKeyPassword
                        )
                        sign(this@withType)
                    }
                }
            }
        }
    }
}

// Dokka implementation
tasks.withType<DokkaTask>().configureEach {
    moduleName.set("SD-JWT KMP")
    moduleVersion.set(rootProject.version.toString())
    description = "SD-JWT KMP Wrapper of Rust Implementation"
    dokkaSourceSets {
        configureEach {
            jdkVersion.set(17)
            languageVersion.set("1.9.23")
            apiVersion.set("2.0")
            sourceLink {
                localDirectory.set(projectDir.resolve("src"))
                remoteUrl.set(URL("https://github.com/input-output-hk/sd-jwt-rust/tree/main/src"))
                remoteLineSuffix.set("#L")
            }
            externalDocumentationLink {
                url.set(URL("https://kotlinlang.org/api/latest/jvm/stdlib/"))
            }
            externalDocumentationLink {
                url.set(URL("https://kotlinlang.org/api/kotlinx.serialization/"))
            }
            externalDocumentationLink {
                url.set(URL("https://api.ktor.io/"))
            }
            externalDocumentationLink {
                url.set(URL("https://kotlinlang.org/api/kotlinx-datetime/"))
                packageListUrl.set(URL("https://kotlinlang.org/api/kotlinx-datetime/"))
            }
            externalDocumentationLink {
                url.set(URL("https://kotlinlang.org/api/kotlinx.coroutines/"))
            }
        }
    }
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://oss.sonatype.org/content/repositories/snapshots/"))
            username.set(System.getenv("SONATYPE_USERNAME"))
            password.set(System.getenv("SONATYPE_PASSWORD"))
        }
    }
}

afterEvaluate {
    tasks.withType<PublishToMavenRepository> {
        dependsOn(tasks.withType<Sign>())
    }
    tasks.withType<PublishToMavenLocal> {
        dependsOn(tasks.withType<Sign>())
    }
    tasks.withType<KotlinCompile> {
        dependsOn(buildRust)
    }
    tasks.withType<ProcessResources> {
        dependsOn(buildRust)
    }
    tasks.withType<ProcessAndroidResources> {
        dependsOn(copyBindings)
    }
    tasks.withType<SourceJarTask> {
        dependsOn(copyBindings)
    }
    tasks.withType<org.gradle.jvm.tasks.Jar> {
        dependsOn(copyBindings)
    }
    tasks.withType<KtLintCheckTask> {
        dependsOn(copyBindings)
    }
    tasks.named("packageDebugResources") {
        dependsOn(copyBindings)
    }
    tasks.named("packageReleaseResources") {
        dependsOn(copyBindings)
    }
    tasks.named("extractDeepLinksForAarDebug") {
        dependsOn(copyBindings)
    }
    tasks.named("extractDeepLinksForAarRelease") {
        dependsOn(copyBindings)
    }
    tasks.named("androidReleaseSourcesJar") {
        dependsOn(copyBindings)
    }
    tasks.named("androidDebugSourcesJar") {
        dependsOn(copyBindings)
    }
    tasks.named("jvmSourcesJar") {
        dependsOn(copyBindings)
    }
    tasks.named("sourcesJar") {
        dependsOn(copyBindings)
    }
    tasks.named("mergeDebugJniLibFolders") {
        dependsOn(copyGeneratedBinaryForAndroid)
    }
    tasks.named("mergeReleaseJniLibFolders") {
        dependsOn(copyGeneratedBinaryForAndroid)
    }
    tasks.named("mergeReleaseResources") {
        dependsOn(copyBindings)
    }
    tasks.named("mergeDebugResources") {
        dependsOn(copyBindings)
    }
    tasks.named("generateReleaseResValues") {
        dependsOn(copyBindings)
    }
    tasks.named("generateDebugResValues") {
        dependsOn(copyBindings)
    }
    if (tasks.findByName("generateReleaseAndroidTestResValues") != null) {
        tasks.named("generateReleaseAndroidTestResValues") {
            dependsOn(copyBindings)
        }
    }
    if (tasks.findByName("generateDebugAndroidTestResValues") != null) {
        tasks.named("generateDebugAndroidTestResValues") {
            dependsOn(copyBindings)
        }
    }
}
