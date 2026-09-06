import org.gradle.api.tasks.SourceSetContainer

plugins {
    java
}

group = rootProject.group
version = rootProject.version

val bundled by configurations.creating

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.opencollab.dev/main/")
}

dependencies {
    implementation(project(":protocol"))
    compileOnly("com.velocitypowered:velocity-api:3.4.0")
    annotationProcessor("com.velocitypowered:velocity-api:3.4.0")
    compileOnly("org.geysermc.geyser:api:2.8.2-SNAPSHOT")
    compileOnly("com.google.code.gson:gson:2.11.0")
    bundled("com.google.code.gson:gson:2.11.0")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.geysermc.geyser:api:2.8.2-SNAPSHOT")
    testImplementation("com.google.code.gson:gson:2.11.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

evaluationDependsOn(":protocol")
val protocolSourceSets = project(":protocol").extensions.getByType<SourceSetContainer>()

tasks.jar {
    archiveBaseName.set("BedrockBridge-Velocity")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(protocolSourceSets.named("main").map { it.output })
    from(bundled.map { dependency ->
        if (dependency.isDirectory) dependency else zipTree(dependency)
    })
}

tasks.test {
    useJUnitPlatform()
}
