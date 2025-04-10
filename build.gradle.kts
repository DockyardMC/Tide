plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
}

group = "io.github.dockyard.tide"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://mvn.devos.one/releases")
    maven("https://mvn.devos.one/snapshots")
    maven("https://jitpack.io")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("cz.lukynka:pretty-log:1.5")
    api("io.ktor:ktor-server-netty:3.1.2")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.3.0")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}