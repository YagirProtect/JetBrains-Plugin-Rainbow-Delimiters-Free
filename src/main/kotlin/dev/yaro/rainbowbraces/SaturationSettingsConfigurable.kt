package dev.yaro.rainbowbraces

import com.intellij.openapi.options.SearchableConfigurable
import java.awt.BorderLayout
import javax.swing.*
import javax.swing.BoxLayout

class SaturationSettingsConfigurable : SearchableConfigurable {
    private val settings = SaturationSettings.getInstance()
    private var panel: JPanel? = null
    private lateinit var saturationSlider: JSlider
    private lateinit var saturationLabel: JLabel
    private lateinit var brightnessSlider: JSlider
    private lateinit var brightnessLabel: JLabel

    override fun getId() = "rainbow.braces.saturation"
    override fun getDisplayName() = "彩虹括号饱和度与亮度"

    override fun createComponent(): JComponent {
        panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }

        // 饱和度行
        val saturationRow = JPanel(BorderLayout(10, 10))
        saturationRow.add(JLabel("饱和度:"), BorderLayout.WEST)
        saturationSlider = JSlider(0, 100, (settings.saturation * 100).toInt())
        saturationSlider.majorTickSpacing = 20
        saturationSlider.paintTicks = true
        saturationSlider.paintLabels = true
        saturationRow.add(saturationSlider, BorderLayout.CENTER)
        saturationLabel = JLabel("${(settings.saturation * 100).toInt()}%")
        saturationRow.add(saturationLabel, BorderLayout.EAST)
        saturationSlider.addChangeListener {
            saturationLabel.text = "${saturationSlider.value}%"
        }
        panel!!.add(saturationRow)

        // 亮度行
        val brightnessRow = JPanel(BorderLayout(10, 10))
        brightnessRow.add(JLabel("亮度:"), BorderLayout.WEST)
        brightnessSlider = JSlider(0, 100, (settings.brightness * 100).toInt())
        brightnessSlider.majorTickSpacing = 20
        brightnessSlider.paintTicks = true
        brightnessSlider.paintLabels = true
        brightnessRow.add(brightnessSlider, BorderLayout.CENTER)
        brightnessLabel = JLabel("${(settings.brightness * 100).toInt()}%")
        brightnessRow.add(brightnessLabel, BorderLayout.EAST)
        brightnessSlider.addChangeListener {
            brightnessLabel.text = "${brightnessSlider.value}%"
        }
        panel!!.add(brightnessRow)

        return panel!!
    }

    override fun isModified(): Boolean {
        return saturationSlider.value != (settings.saturation * 100).toInt() ||
                brightnessSlider.value != (settings.brightness * 100).toInt()
    }

    override fun apply() {
        settings.saturation = saturationSlider.value / 100f
        settings.brightness = brightnessSlider.value / 100f
    }

    override fun reset() {
        saturationSlider.value = (settings.saturation * 100).toInt()
        brightnessSlider.value = (settings.brightness * 100).toInt()
    }

    override fun disposeUIResources() {
        panel = null
    }

    override fun getHelpTopic() = null
}