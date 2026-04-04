package dev.yaro.rainbowbraces

import com.intellij.ui.JBColor
import java.awt.Color

object MyPluginPalette {
    private val BASE_PALETTE: Array<JBColor> = arrayOf(
        JBColor(Color(0xC62828), Color(0xFF6B6B)),
        JBColor(Color(0xAD1457), Color(0xFF4D9D)),
        JBColor(Color(0x6A1B9A), Color(0xB388FF)),
        JBColor(Color(0x283593), Color(0x82B1FF)),
        JBColor(Color(0x1565C0), Color(0x4FC3F7)),
        JBColor(Color(0x00695C), Color(0x64FFDA)),
        JBColor(Color(0x2E7D32), Color(0xB9F6CA)),
        JBColor(Color(0xF9A825), Color(0xFFE082)),
    )

    val PALETTE: Array<JBColor>
        get() {
            val saturation = SaturationSettings.getInstance().saturation
            return BASE_PALETTE.map {
                ColorSaturationUtil.adjustJBColorSaturation(it, saturation)
            }.toTypedArray()
        }
}