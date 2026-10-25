// Copyright (c) 2026 YagirProtect.
// Licensed under GPL-3.0-only with the JetBrains IDE Linking Exception.
// See LICENSE and LICENSE-EXCEPTION in the project root.

package dev.yaro.rainbowbraces.palette

import java.awt.Color

/**
 * Shared color transform for editor rendering and unsaved palette previews.
 * Percentages multiply the source saturation/brightness, preserving hue and the
 * differences between colors rather than assigning one absolute HSB value to all.
 * Returning a new display color leaves import/export and picker values untouched.
 */
internal object PaletteColorAdjustment {
    fun adjust(rgb: Int, saturationPercent: Int, brightnessPercent: Int): Color {
        val source = Color(rgb and 0xFFFFFF)
        val saturation = saturationPercent.coerceIn(0, 100)
        val brightness = brightnessPercent.coerceIn(0, 100)
        // Avoid an RGB -> HSB -> RGB round trip at the default values: rounding could
        // otherwise subtly change source colors even when no adjustment was requested.
        if (saturation == 100 && brightness == 100) return source
        val hsb = Color.RGBtoHSB(source.red, source.green, source.blue, null)
        return Color.getHSBColor(hsb[0], hsb[1] * saturation / 100f, hsb[2] * brightness / 100f)
    }
}
