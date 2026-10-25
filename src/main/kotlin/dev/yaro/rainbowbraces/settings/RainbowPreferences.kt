package dev.yaro.rainbowbraces.settings

import dev.yaro.rainbowbraces.palette.RainbowBuiltInPalettes
import dev.yaro.rainbowbraces.palette.RainbowPalette

/** Defaults for fresh settings and the explicit Defaults button, not an upgrade migration. */
object RainbowDefaultFileTypes {
    val extensions: List<String> = java.util.List.copyOf(listOf(
        "rs", "cs",
        "java", "kt", "kts",
        "c", "h", "cc", "cpp", "cxx", "hpp", "hh", "hxx", "ixx", "mxx", "cppm", "ccm", "cxxm", "c++m",
        "js", "ts", "jsx", "tsx",
        "py", "go", "swift", "lua", "json", "hlsl", "shader", "php",
        "glsl", "vert", "vsh", "tesc", "tese", "geom", "gsh", "frag", "fsh", "comp"
    ))
}

/**
 * Complete settings value shared by the service and the settings-page draft.
 * Percentages describe transparency (100 means invisible) or relative HSB intensity
 * (100 preserves the source color). They never alter the stored palette RGB values.
 * Callers may supply mutable lists, so the service normalizes and freezes collections
 * on acceptance rather than relying on Kotlin's read-only List interface alone.
 */
data class RainbowPreferences(
    val enabled: Boolean = true,
    val pairEmphasisEnabled: Boolean = true,
    val indentGuidesEnabled: Boolean = true,
    val activeIndentGuideTransparency: Int = DEFAULT_ACTIVE_GUIDE_TRANSPARENCY,
    val inactiveIndentGuideTransparency: Int = DEFAULT_INACTIVE_GUIDE_TRANSPARENCY,
    val colorSaturation: Int = DEFAULT_COLOR_SATURATION,
    val colorBrightness: Int = DEFAULT_COLOR_BRIGHTNESS,
    val activePaletteId: String = RainbowBuiltInPalettes.default.id,
    val palettes: List<RainbowPalette> = java.util.List.copyOf(RainbowBuiltInPalettes.all.map { it.toPalette() }),
    val enabledExtensions: List<String> = RainbowDefaultFileTypes.extensions
) {
    companion object {
        const val DEFAULT_ACTIVE_GUIDE_TRANSPARENCY = 0
        const val DEFAULT_INACTIVE_GUIDE_TRANSPARENCY = 75
        const val DEFAULT_COLOR_SATURATION = 100
        const val DEFAULT_COLOR_BRIGHTNESS = 100
    }
}

/** Canonicalize user input; an empty selection falls back to the current default list. */
internal fun normalizeExtensions(extensions: Collection<String>): List<String> =
    java.util.List.copyOf(extensions.map { it.trim().removePrefix(".").lowercase() }
        .filter { it.isNotBlank() }.distinct().sorted().ifEmpty { RainbowDefaultFileTypes.extensions.sorted() })

/**
 * Establish service invariants at the write boundary, keeping normal reads cheap.
 * Restore missing built-in identities while retaining the user's edits to their colors.
 * Existing nonempty extension selections are preserved: merging new defaults here
 * would silently re-enable extensions that a user deliberately removed.
 */
internal fun RainbowPreferences.normalized(): RainbowPreferences {
    val complete = RainbowBuiltInPalettes.all.map { builtIn ->
        palettes.firstOrNull { it.id == builtIn.id }?.copy(builtInId = builtIn.id) ?: builtIn.toPalette()
    } + palettes.filter { RainbowBuiltInPalettes.find(it.id) == null }
    return copy(
        activeIndentGuideTransparency = activeIndentGuideTransparency.coerceIn(0, 100),
        inactiveIndentGuideTransparency = inactiveIndentGuideTransparency.coerceIn(0, 100),
        colorSaturation = colorSaturation.coerceIn(0, 100),
        colorBrightness = colorBrightness.coerceIn(0, 100),
        activePaletteId = activePaletteId.takeIf { id -> complete.any { it.id == id } } ?: complete.first().id,
        palettes = java.util.List.copyOf(complete),
        enabledExtensions = normalizeExtensions(enabledExtensions)
    )
}
