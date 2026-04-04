package dev.yaro.rainbowbraces

import com.intellij.openapi.options.Configurable
import javax.swing.*
import java.awt.*

class SaturationSettingsConfigurable : Configurable {
    private val settings = SaturationSettings.getInstance()
    private var panel: JPanel? = null
    private lateinit var saturationSlider: JSlider
    private lateinit var valueLabel: JLabel

    override fun getDisplayName(): String = "调色板饱和度设置"

    override fun createComponent(): JComponent? {
        panel = JPanel(BorderLayout(10, 10)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(JLabel("颜色饱和度："), BorderLayout.WEST)

            saturationSlider = JSlider(0, 100, (settings.saturation * 100).toInt()).apply {
                majorTickSpacing = 20
                paintTicks = true
                paintLabels = true
                addChangeListener {
                    val value = saturationSlider.value / 100f
                    valueLabel.text = "当前：${(value * 100).toInt()}%"
                }
            }
            add(saturationSlider, BorderLayout.CENTER)

            valueLabel = JLabel("当前：${(settings.saturation * 100).toInt()}%")
            add(valueLabel, BorderLayout.EAST)
        }
        return panel
    }

    override fun apply() {
        settings.saturation = saturationSlider.value / 100f
        com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents()
    }

    override fun isModified(): Boolean = saturationSlider.value != (settings.saturation * 100).toInt()
    override fun reset() = saturationSlider.value = (settings.saturation * 100).toInt()
    override fun disposeUIResources() { panel = null }
    override fun getHelpTopic(): String? = null
}