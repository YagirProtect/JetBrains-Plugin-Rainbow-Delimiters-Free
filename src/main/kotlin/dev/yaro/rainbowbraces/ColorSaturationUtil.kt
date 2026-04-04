package dev.yaro.rainbowbraces

import com.intellij.ui.JBColor
import java.awt.Color

object ColorSaturationUtil {
    fun adjustJBColorSaturation(original: JBColor, saturation: Float): JBColor {
        val darkColor = adjustColorSaturation(original.darkColor, saturation)
        val lightColor = adjustColorSaturation(original.lightColor, saturation)
        return JBColor(lightColor, darkColor)
    }

    private fun adjustColorSaturation(color: Color, saturation: Float): Color {
        val hsb = Color.RGBtoHSB(color.red, color.green, color.blue, null)
        return Color.getHSBColor(hsb[0], saturation.coerceIn(0f, 1f), hsb[2])
    }
}