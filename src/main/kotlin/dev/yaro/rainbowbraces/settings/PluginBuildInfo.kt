// Copyright (c) 2026 YagirProtect.
// Licensed under GPL-3.0-only with the JetBrains IDE Linking Exception.
// See LICENSE and LICENSE-EXCEPTION in the project root.

package dev.yaro.rainbowbraces.settings

import java.util.Properties

/**
 * Build identity expanded by Gradle from the plugin descriptor/ZIP version.
 * A plugin-specific classpath resource avoids depending on an initialized IDE plugin
 * registry, including in settings tests. Fail early if packaging omitted expansion;
 * persisting a placeholder would make future version-based migrations unreliable.
 */
internal object PluginBuildInfo {
    val version: String = Properties().apply {
        val resource = checkNotNull(PluginBuildInfo::class.java.getResourceAsStream("/META-INF/rainbow-delimiters.properties")) {
            "Missing plugin build version resource"
        }
        resource.use { load(it) }
    }.getProperty("version").also {
        check(!it.isNullOrBlank() && !it.contains('$')) { "Plugin build version was not expanded" }
    }
}
