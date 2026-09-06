import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar

plugins {
    kotlin("jvm") version "2.2.10"
    id("com.typewritermc.module-plugin") version "2.0.0"
}

group = "dev.rafo"
version = "0.1.0-SNAPSHOT"

dependencies {
    implementation(project(":protocol"))
    compileOnly("com.google.code.gson:gson:2.11.0")

    testImplementation("com.google.code.gson:gson:2.11.0")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

typewriter {
    namespace = "lusiadascraft"

    extension {
        name = "BedrockBridge"
        shortDescription = "Transparent Bedrock compatibility layer for Typewriter."
        description = "Bridges Typewriter experiences to Bedrock/Geyser with automatic HUD, audio, camera and fallback compatibility fixes while leaving Java behavior untouched."
        engineVersion = "0.9.0-beta-167"

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
    // Typewriter declares this runtime integration transitively, but BedrockBridge does not use it.
    // The server supplies Typewriter's own runtime dependencies.
    exclude(group = "me.tofaa.entitylib", module = "spigot")
}

evaluationDependsOn(":protocol")
val protocolSourceSets = project(":protocol").extensions.getByType<SourceSetContainer>()

tasks.named<Jar>("jar") {
    from(protocolSourceSets.named("main").map { it.output })
}
