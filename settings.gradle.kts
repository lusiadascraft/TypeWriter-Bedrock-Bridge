pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.typewritermc.com/releases")
    }
}

rootProject.name = "BedrockBridge"

include("protocol", "velocity")
