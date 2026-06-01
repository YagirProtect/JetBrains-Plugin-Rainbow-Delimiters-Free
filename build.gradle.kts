import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = "com.yaro.rainbowdelimiters"
version = "1.4.6"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

val targetIde = ((findProperty("targetIde") as String?) ?: "rider").lowercase()
val localIdePath = (findProperty("localIdePath") as String?)
    ?: when (targetIde) {
        "rider" -> findProperty("riderIdePath") as String?
        "rustrover" -> findProperty("rustRoverIdePath") as String?
        else -> null
    }

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        if (localIdePath != null) {
            local(localIdePath)
        } else {
            rider("2026.1.2")
        }
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)


        // Add plugin dependencies for compilation here, example:
        // bundledPlugin("com.intellij.java")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
            untilBuild = "261.*"
        }

        changeNotes = """
            Added editable palette names, dynamic palette size, color add/remove actions and palette import/export.
            Added enable/disable toggle and editable supported file extensions.
            Added optional matching pair emphasis without rescanning on caret movement.
            Reduced editor overhead with cached delimiter scans and safer highlighter limits.
            Targets IntelliJ Platform 2025.1 through 2026.1.
        """.trimIndent()
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

tasks {
    patchPluginXml {
        sinceBuild.set("251")
        untilBuild.set("261.*")
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}
