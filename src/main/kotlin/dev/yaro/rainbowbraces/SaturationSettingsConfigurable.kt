package dev.yaro.rainbowbraces

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.JBColor
import java.awt.*
import javax.swing.*
import javax.swing.BoxLayout

class SaturationSettingsConfigurable : SearchableConfigurable {
    private val settings = SaturationSettings.getInstance()
    private var panel: JPanel? = null
    private lateinit var saturationSlider: JSlider
    private lateinit var saturationLabel: JLabel
    private lateinit var brightnessSlider: JSlider
    private lateinit var brightnessLabel: JLabel
    private lateinit var previewPanel: JPanel   // 预览面板

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
            updatePreview()   // 实时更新预览
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
            updatePreview()   // 实时更新预览
        }
        panel!!.add(brightnessRow)

        // 预览区域
        previewPanel = JPanel().apply {
            layout = FlowLayout(FlowLayout.LEFT, 10, 10)
            border = BorderFactory.createTitledBorder("颜色预览")
            preferredSize = Dimension(0, 80)
        }
        panel!!.add(previewPanel)

        // 初始化预览
        updatePreview()

        return panel!!
    }

    /**
     * 根据当前滑块的值生成预览色块
     */
    private fun updatePreview() {
        val currentSaturation = saturationSlider.value / 100f
        val currentBrightness = brightnessSlider.value / 100f
        val palette = MyPluginPalette.generatePalette(currentSaturation, currentBrightness)

        previewPanel.removeAll()
        for (color in palette) {
            val colorPanel = object : JPanel() {
                override fun paintComponent(g: Graphics) {
                    super.paintComponent(g)
                    g.color = color
                    g.fillRect(0, 0, width, height)
                    g.color = JBColor.GRAY
                    g.drawRect(0, 0, width - 1, height - 1)
                }
            }
            colorPanel.preferredSize = Dimension(40, 40)
            previewPanel.add(colorPanel)
        }
        previewPanel.revalidate()
        previewPanel.repaint()
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
        updatePreview()
    }

    override fun disposeUIResources() {
        panel = null
    }

    override fun getHelpTopic() = null
}