package dev.yaro.rainbowbraces.palette

import java.awt.Color
import junit.framework.TestCase
import kotlin.math.abs

class PaletteColorAdjustmentTest : TestCase() {
    fun testDefaultsPreserveEveryOriginalColorExactly() {
        val colors = RainbowBuiltInPalettes.all.flatMap { it.lightColors + it.darkColors } + listOf(0, 0xFFFFFF, 0x123456)
        for (rgb in colors) assertEquals(rgb, PaletteColorAdjustment.adjust(rgb, 100, 100).rgb and 0xFFFFFF)
    }

    fun testZeroSaturationIsGrayscaleAndPreservesBrightness() {
        val source = Color(0x2468AC)
        val result = PaletteColorAdjustment.adjust(source.rgb, 0, 100)
        assertEquals(result.red, result.green)
        assertEquals(result.green, result.blue)
        assertEquals(maxOf(source.red, source.green, source.blue), result.red)
    }

    fun testRelativeControlsKeepHueAndScaleOriginalSaturationAndBrightness() {
        val source = Color(0x3C78B4)
        val original = Color.RGBtoHSB(source.red, source.green, source.blue, null)
        val adjusted = PaletteColorAdjustment.adjust(source.rgb, 50, 60)
        val result = Color.RGBtoHSB(adjusted.red, adjusted.green, adjusted.blue, null)
        assertTrue(abs(original[0] - result[0]) < 0.01f)
        assertTrue(abs(original[1] * 0.5f - result[1]) < 0.01f)
        assertTrue(abs(original[2] * 0.6f - result[2]) < 0.01f)
    }

    fun testZeroBrightnessIsBlackForEverySaturation() {
        for (saturation in listOf(0, 50, 100)) {
            assertEquals(Color.BLACK, PaletteColorAdjustment.adjust(0xAA7733, saturation, 0))
        }
    }

    fun testOutOfRangeControlsAreClamped() {
        assertEquals(Color(0x123456), PaletteColorAdjustment.adjust(0x123456, 200, 200))
        assertEquals(Color.BLACK, PaletteColorAdjustment.adjust(0x123456, -10, -10))
    }
}
