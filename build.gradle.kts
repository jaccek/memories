plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    application
}

group = "com.github.jaccek"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    val ktorVersion = "3.3.3"

    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")
    implementation("com.drewnoakes:metadata-extractor:2.19.0")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.20")

    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.github.jaccek.infrastructure.MainKt")
}

tasks.test {
    useJUnitPlatform()
}