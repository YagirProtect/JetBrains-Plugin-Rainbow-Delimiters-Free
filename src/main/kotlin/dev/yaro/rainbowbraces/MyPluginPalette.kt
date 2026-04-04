package dev.yaro.rainbowbraces

import com.intellij.ui.JBColor
import java.awt.Color

object MyPluginPalette {
    private val BASE_PALETTE: Array<Pair<Color, Color>> = arrayOf(
        Color(0xC62828) to Color(0xFF6B6B),
        Color(0xAD1457) to Color(0xFF4D9D),
        Color(0x6A1B9A) to Color(0xB388FF),
        Color(0x283593) to Color(0x82B1FF),
        Color(0x1565C0) to Color(0x4FC3F7),
        Color(0x00695C) to Color(0x64FFDA),
        Color(0x2E7D32) to Color(0xB9F6CA),
        Color(0xF9A825) to Color(0xFFE082),
    )

    fun adjustColor(color: Color, saturation: Float, brightness: Float): Color {
        val hsb = Color.RGBtoHSB(color.red, color.green, color.blue, null)
        // 保持原始色相，应用用户设置的饱和度和亮度
        return Color.getHSBColor(
            hsb[0],
            saturation.coerceIn(0f, 1f),
            brightness.coerceIn(0f, 1f)
        )
    }

    val PALETTE: Array<JBColor>
        get() {
            val settings = runCatching { SaturationSettings.getInstance() }.getOrNull()
            val saturation = settings?.saturation ?: 1.0f
            val brightness = settings?.brightness ?: 1.0f

            return BASE_PALETTE.map { (light, dark) ->
                val adjustedLight = adjustColor(light, saturation, brightness)
                val adjustedDark = adjustColor(dark, saturation, brightness)
                JBColor(adjustedLight, adjustedDark)
            }.toTypedArray()
        }
}