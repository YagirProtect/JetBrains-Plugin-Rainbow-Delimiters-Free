package dev.yaro.rainbowbraces.settings

import dev.yaro.rainbowbraces.palette.RainbowBuiltInPalettes
import dev.yaro.rainbowbraces.palette.RainbowPalette

// Mutable beans are exclusively the XML persistence format. The service never exposes its stored state through them.
class RainbowPaletteState {
    var id: String = ""
    var name: String = ""
    var builtInId: String = ""
    var lightColors: MutableList<Int> = arrayListOf()
    var darkColors: MutableList<Int> = arrayListOf()
}

/**
 * XML serialization schema. Public mutable properties and no-argument construction
 * are intentional requirements of the platform serializer, not the live settings API.
 * Keep legacy property names readable when evolving this schema so loadState can
 * migrate old installations before constructing validated preferences.
 */
class RainbowSettingsState {
    // Empty means the settings predate version tracking; do not infer an old plugin version.
    var lastSavedPluginVersion: String = ""
    var enabled: Boolean = true
    var pairEmphasisEnabled: Boolean = true
    var indentGuidesEnabled: Boolean = true
    var activeIndentGuideTransparency: Int = DEFAULT_ACTIVE_GUIDE_TRANSPARENCY
    var inactiveIndentGuideTransparency: Int = DEFAULT_INACTIVE_GUIDE_TRANSPARENCY
    var colorSaturation: Int = RainbowPreferences.DEFAULT_COLOR_SATURATION
    var colorBrightness: Int = RainbowPreferences.DEFAULT_COLOR_BRIGHTNESS
    var activePaletteId: String = RainbowBuiltInPalettes.default.id

    // Kept for migration from versions where the user could limit active colors separately.
    var colorCount: Int = DEFAULT_COLOR_COUNT

    // Kept for migration from version 1.3.0-dev where the selected palette was an enum name.
    var paletteName: String = ""
    var palettes: MutableList<RainbowPaletteState> = arrayListOf()
    var enabledExtensions: MutableList<String> = ArrayList(RainbowDefaultFileTypes.extensions)

    companion object {
        const val DEFAULT_ACTIVE_GUIDE_TRANSPARENCY = RainbowPreferences.DEFAULT_ACTIVE_GUIDE_TRANSPARENCY
        const val DEFAULT_INACTIVE_GUIDE_TRANSPARENCY = RainbowPreferences.DEFAULT_INACTIVE_GUIDE_TRANSPARENCY
        const val MIN_COLOR_COUNT = RainbowPalette.MIN_COLOR_COUNT
        const val DEFAULT_COLOR_COUNT = 8
    }
}


internal fun RainbowPalette.toState() = RainbowPaletteState().also {
    it.id = id
    it.name = name
    it.builtInId = builtInId
    it.lightColors = ArrayList(lightColors)
    it.darkColors = ArrayList(darkColors)
}

/**
 * Repair incomplete persisted palettes before invoking the domain constructor.
 * RGB integers exclude alpha; missing theme variants inherit the light colors and
 * very short palettes are padded to the minimum required by the scanner/UI.
 */
internal fun RainbowPaletteState.toPalette(): RainbowPalette {
    val colors = lightColors.map { it and 0xFFFFFF }.toMutableList()
    while (colors.size < RainbowSettingsState.MIN_COLOR_COUNT) {
        colors.add(RainbowBuiltInPalettes.default.lightColors[colors.size])
    }
    val dark = if (darkColors.size == colors.size) darkColors.map { it and 0xFFFFFF } else colors
    return RainbowPalette(id.ifBlank { "custom-${java.util.UUID.randomUUID()}" }, name, builtInId, colors, dark)
}
