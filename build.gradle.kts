import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "com.yaro.rainbowdelimiters"
version = "1.9.2"

tasks.processResources {
    val buildVersion = project.version.toString()
    inputs.property("pluginBuildVersion", buildVersion)
    filesMatching("META-INF/rainbow-delimiters.properties") {
        expand("pluginVersion" to buildVersion)
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

val targetIde = ((findProperty("targetIde") as String?) ?: "idea").lowercase()
val configuredIdePath = when (targetIde) {
    "rider" -> findProperty("riderIdePath") as String?
    "rustrover" -> findProperty("rustRoverIdePath") as String?
    "idea" -> findProperty("ideaIdePath") as String?
    else -> null
}?.takeIf { file(it).resolve("product-info.json").isFile }
val localIdePath = (findProperty("localIdePath") as String?) ?: configuredIdePath

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        if (localIdePath != null) {
            local(localIdePath)
        } else {
            when (targetIde) {
                "rider" -> rider("2026.2.3.1")
                "rustrover" -> rustRover("2026.2.3")
                "idea" -> intellijIdea("2026.2.3")
                else -> error("Unsupported targetIde: $targetIde (use rider, rustrover, or idea)")
            }
        }
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)


        // Add plugin dependencies for compilation here, example:
        // bundledPlugin("com.intellij.java")
    }
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginVerification {
        ides {
            current()
            providers.gradleProperty("verificationIdePath").orNull?.let { local(file(it)) }
            // Include the oldest supported platform when explicitly requested.
            if (providers.gradleProperty("verifyOldestIde").isPresent) {
                create(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaCommunity, "2025.1")
            }
        }
    }
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
            untilBuild = "262.*"
        }

        changeNotes = """
            Settings now record the plugin version to support future migrations.
            Saturation and brightness now preview directly in every palette row using the current IDE theme.
            Preserved original palette colors at 100% and cached adjusted colors for editor highlighting.
            Added C++ module and GLSL shader extensions to the defaults.
            Added theme-gray indent guides for unbraced switch case/default sections in C#, C and C++, including when guards.
            Added colored indent guides for multiline parentheses and square brackets, including function parameters and arrays.
            Fixed one-pixel indent guide gaps at fractional display scales, including Rider.
            Fixed duplicate native and colored indent guides in Rider, including split editors.
            Fixed indent guide gaps around Code Vision hints such as usages and implementations.
            Changed the default inactive indent guide transparency to 75%.
            Separated delimiter scanning, editor lifecycle, rendering, settings storage and palette editing.
            Encapsulated settings and palettes with immutable snapshots and defensive serialization copies.
            Replaced regular-expression palette parsing with JSON parsing, preserving legacy imports and dark colors.
            Preserved highlighting behavior, saved settings and compatibility with IntelliJ Platform 2025.1 through 2026.2.
        """.trimIndent()
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "21"
    targetCompatibility = "21"
}

kotlin {
    jvmToolchain(25)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        // 2025.1 bundles Kotlin 2.1; avoid requiring a newer stdlib at runtime.
        languageVersion.set(KotlinVersion.KOTLIN_2_1)
        apiVersion.set(KotlinVersion.KOTLIN_2_1)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

// Editor tests use platform APIs and do not require licensed Ultimate services.
tasks.named<org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask>("prepareTestSandbox") {
    disabledPlugins.add("com.intellij.modules.ultimate")
}
