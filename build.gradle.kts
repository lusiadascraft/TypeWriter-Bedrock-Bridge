plugins {
    kotlin("jvm") version "2.0.21"
    id("com.typewritermc.module-plugin") version "1.1.3"
}

group = "dev.rafo"
version = "0.1.0-SNAPSHOT"

dependencies {
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

typewriter {
    namespace = "lusiadascraft"

    extension {
        name = "BedrockBridge"
        shortDescription = "Transparent Bedrock compatibility layer for Typewriter."
        description = "Bridges Typewriter experiences to Bedrock/Geyser with automatic HUD, audio, camera and fallback compatibility fixes while leaving Java behavior untouched."
        engineVersion = "0.8.0"

        paper()
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

configurations.configureEach {
    // Typewriter 0.8.0 publishes an obsolete EntityLib snapshot from a retired repository.
    // BedrockBridge does not use EntityLib; Typewriter provides it at runtime for its own code.
    exclude(group = "me.tofaa.entitylib", module = "spigot")
}
