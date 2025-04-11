plugins {
    kotlin("jvm") version "2.1.0"
}

group = "io.github.dockyard.tide"
version = "1.0"

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
    api("com.google.code.gson:gson:2.13.0")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}