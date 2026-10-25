// Copyright (c) 2026 YagirProtect.
// Licensed under GPL-3.0-only with the JetBrains IDE Linking Exception.
// See LICENSE and LICENSE-EXCEPTION in the project root.

package dev.yaro.rainbowbraces.palette

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Pure JSON interchange, separate from XML persistence, file I/O and Swing dialogs.
 * Version-1 exports use colors for the light variant; optional darkColors extends
 * that format while allowing older files to use the same RGB values in both themes.
 * Exports contain raw palette values so repeated export/import never compounds the
 * saturation or brightness transform. Parse/validation errors propagate to the UI.
 */
internal object PaletteJsonCodec {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun encode(activePaletteId: String, palettes: List<RainbowPalette>): String {
        val root = JsonObject()
        root.addProperty("version", 1)
        root.addProperty("activePaletteId", activePaletteId)
        root.add("palettes", JsonArray().apply {
            palettes.forEach { palette ->
                add(JsonObject().apply {
                    addProperty("id", palette.id)
                    addProperty("name", palette.name)
                    addProperty("builtInId", palette.builtInId)
                    add("colors", encodeColors(palette.lightColors))
                    if (palette.darkColors != palette.lightColors) add("darkColors", encodeColors(palette.darkColors))
                })
            }
        })
        return gson.toJson(root)
    }

    fun decode(text: String): List<RainbowPalette> {
        // Return palette values only. Import selection/merge policy belongs to the
        // editor model, so the exported activePaletteId is not applied here.
        val root = JsonParser.parseString(text).asJsonObject
        val entries = root.getAsJsonArray("palettes") ?: return emptyList()
        return entries.mapNotNull { entry ->
            val palette = entry.asJsonObject
            val colors = decodeColors(palette.getAsJsonArray("colors") ?: return@mapNotNull null)
            if (colors.size < RainbowPalette.MIN_COLOR_COUNT) return@mapNotNull null
            // Skip unusably short entries, but reject mismatched theme arrays rather
            // than silently pairing colors with incorrect nesting indices.
            val darkColors = palette.getAsJsonArray("darkColors")?.let(::decodeColors) ?: colors
            require(darkColors.size == colors.size) { "Light and dark palettes must have equal color counts" }
            RainbowPalette(
                id = palette.string("id").ifBlank { "custom-${java.util.UUID.randomUUID()}" },
                name = palette.string("name").ifBlank { "Imported palette" },
                builtInId = palette.string("builtInId"),
                lightColors = colors,
                darkColors = darkColors
            )
        }
    }

    private fun encodeColors(colors: List<Int>) = JsonArray().apply {
        colors.forEach { add("#${(it and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')}") }
    }

    private fun decodeColors(colors: JsonArray): List<Int> = colors.map {
        val hex = it.asString.removePrefix("#")
        require(hex.length == 6) { "Colors must use six hexadecimal digits" }
        hex.toInt(16)
    }

    private fun JsonObject.string(key: String): String = get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
}
