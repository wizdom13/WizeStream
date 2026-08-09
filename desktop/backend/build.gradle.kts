import org.gradle.api.tasks.Exec

plugins {
    application
    java
    id("org.jetbrains.kotlin.jvm") version "2.3.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21"
    id("com.google.protobuf") version "0.9.5"
}

group = "org.wisso.wizestream"
version = "0.1.0"

repositories {
    mavenCentral()
    maven(url = "https://dl.cloudsmith.io/public/consensys/maven/maven/")
    maven(url = "https://jitpack.io")
    maven(url = "https://dl.cloudsmith.io/public/libp2p/jvm-libp2p/maven/")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

kotlin {
    jvmToolchain(21)
}

sourceSets {
    main {
        java {
            srcDir("../../app/src/main/java")
            include("org/schabi/newpipe/extractor/**")
            include("org/wisso/wizestream/desktop/backend/**")
        }
        kotlin {
            srcDir("../../app/src/main/java")
            include("org/schabi/newpipe/sync/SyncModels.kt")
            include("org/schabi/newpipe/sync/PairingSecurity.kt")
            include("org/schabi/newpipe/sync/SyncProtocol.kt")
            include("org/schabi/newpipe/sync/DesktopSync*.kt")
        }
        proto {
            srcDir("../../app/src/main/proto")
        }
    }
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.2")
    implementation("com.github.TeamNewPipe:nanojson:e9d656ddb49a412a5a0a5d5ef20ca7ef09549996")
    implementation("com.google.protobuf:protobuf-java:3.11.0")
    implementation("com.github.spotbugs:spotbugs-annotations:4.8.3")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("io.libp2p:jvm-libp2p:1.2.2-RELEASE") {
        exclude(group = "io.netty", module = "netty-codec-native-quic")
        exclude(group = "io.netty", module = "netty-tcnative-boringssl-static")
    }
    implementation("org.apache.commons:commons-lang3:3.8.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.85")
    implementation("org.bouncycastle:bcprov-jdk18on:1.85")
    implementation("org.bouncycastle:bcutil-jdk18on:1.85")
    implementation("org.brotli:dec:0.1.1")
    implementation("org.java-websocket:Java-WebSocket:1.4.1")
    implementation("org.json:json:20250517")
    implementation("org.jsoup:jsoup:1.22.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.mozilla:rhino:1.7.13")
    implementation("org.nibor.autolink:autolink:0.10.0")
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    testImplementation(kotlin("test"))
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.11.0"
    }
}

application {
    mainClass.set("org.wisso.wizestream.desktop.backend.DesktopBackend")
    applicationName = "wizestream-desktop-backend"
}

tasks.test {
    useJUnitPlatform()
}

val runtimeDirectory = layout.buildDirectory.dir("runtime")
tasks.register<Exec>("runtimeImage") {
    dependsOn(tasks.installDist)
    val launcher = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    inputs.property("javaVersion", 21)
    outputs.dir(runtimeDirectory)
    doFirst {
        delete(runtimeDirectory)
        val executable = launcher.get().metadata.installationPath.file(
            "bin/${if (System.getProperty("os.name").startsWith("Windows")) "jlink.exe" else "jlink"}"
        ).asFile
        commandLine(
            executable,
            "--add-modules",
            listOf(
                "java.base", "java.desktop", "java.instrument", "java.logging",
                "java.management", "java.naming", "java.net.http", "java.security.jgss",
                "java.security.sasl", "java.sql", "jdk.crypto.ec", "jdk.unsupported"
            ).joinToString(","),
            "--strip-debug",
            "--no-header-files",
            "--no-man-pages",
            "--compress=2",
            "--output",
            runtimeDirectory.get().asFile
        )
    }
}
