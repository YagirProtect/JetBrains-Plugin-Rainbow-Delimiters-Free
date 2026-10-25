package dev.yaro.rainbowbraces.editor

import dev.yaro.rainbowbraces.RainbowSettingsConfigurable
import dev.yaro.rainbowbraces.palette.ColorSwatch
import dev.yaro.rainbowbraces.palette.PaletteEditorModel
import dev.yaro.rainbowbraces.palette.PaletteEditorPanel
import dev.yaro.rainbowbraces.settings.RainbowSettingsService
import java.awt.Component
import java.awt.Container
import java.awt.Color
import javax.swing.JRadioButton
import javax.swing.JSlider

class PaletteColorSettingsUiTest : RainbowEditorTestCase() {
    private fun descendants(container: Container): List<Component> = container.components.flatMap { child ->
        listOf(child) + if (child is Container) descendants(child) else emptyList()
    }

    private fun swatchColors(container: Container) = descendants(container).filterIsInstance<ColorSwatch>().map { it.color.rgb }

    fun testUnsavedSlidersUpdatePaletteRowsResetAndApplyToBracesAndGuides() {
        configure("    {\n        body();\n    }")
        val service = RainbowSettingsService.getInstance()
        val original = service.preferences()
        val configurable = RainbowSettingsConfigurable()
        try {
            val root = configurable.createComponent()
            val controls = descendants(root)
            val sliders = controls.filterIsInstance<JSlider>().associateBy { it.name }
            val saturation = sliders.getValue("colorSaturation")
            val brightness = sliders.getValue("colorBrightness")
            assertEquals(100, saturation.value)
            assertEquals(100, brightness.value)
            val originalPreview = swatchColors(root)
            saturation.value = 0
            brightness.value = 50
            assertFalse(originalPreview == swatchColors(root))
            assertSame(original, service.preferences())
            assertTrue(configurable.isModified)
            configurable.reset()
            assertEquals(100, saturation.value)
            assertEquals(100, brightness.value)
            assertEquals(originalPreview, swatchColors(root))
            assertFalse(configurable.isModified)
            saturation.value = 30
            brightness.value = 65
            configurable.apply()
            assertEquals(30, service.preferences().colorSaturation)
            assertEquals(65, service.preferences().colorBrightness)
            assertFalse(configurable.isModified)
            val editor = myFixture.editor
            val braceColor = editor.markupModel.allHighlighters.single {
                it.startOffset == 4 && it.endOffset == 5
            }.getTextAttributes(editor.colorsScheme)!!.foregroundColor!!
            assertEquals(service.activeColors().first(), braceColor)
            editor.caretModel.moveToOffset(4)
            val y = editor.offsetToXY(editor.document.getLineStartOffset(1)).y + editor.lineHeight / 2
            assertEquals(braceColor.rgb, render(guides().single()).getRGB(guideX(4), y))
        } finally {
            configurable.disposeUIResources()
        }
    }

    fun testPaletteSelectionKeepsLiveAdjustmentsOnEveryRowBeforeApply() {
        val service = RainbowSettingsService.getInstance()
        val original = service.preferences()
        val configurable = RainbowSettingsConfigurable()
        try {
            val root = configurable.createComponent()
            val controls = descendants(root)
            controls.filterIsInstance<JSlider>().single { it.name == "colorBrightness" }.value = 0
            assertTrue(swatchColors(root).all { it == Color.BLACK.rgb })
            controls.filterIsInstance<JRadioButton>()[1].doClick()
            assertTrue(swatchColors(root).all { it == Color.BLACK.rgb })
            assertSame(original, service.preferences())
            assertTrue(configurable.isModified)
            configurable.apply()
            assertEquals("vivid", service.preferences().activePaletteId)
            assertEquals(0, service.preferences().colorBrightness)
            assertFalse(configurable.isModified)
        } finally {
            configurable.disposeUIResources()
        }
    }

    fun testEditingAndRebuildingPaletteRowsRetainsAdjustmentsWithoutChangingRawColors() {
        val model = PaletteEditorModel()
        val panel = PaletteEditorPanel(model)
        panel.setColorAdjustment(0, 50)
        val before = swatchColors(panel)
        model.setColor(model.activePaletteId, 0, 0x2468AC)
        panel.render()
        val after = swatchColors(panel)
        assertFalse(before[0] == after[0])
        val expected = Color(0x565656).rgb
        assertEquals(expected, after[0])
        assertEquals(0x2468AC, model.palettes.first().lightColors.first())
        val previousSwatchCount = after.size
        panel.duplicateActivePalette()
        assertEquals(expected, swatchColors(panel)[previousSwatchCount])
        panel.setColorAdjustment(100, 100)
        assertEquals(Color(0x2468AC).rgb, swatchColors(panel).first())
    }
}
