// Copyright (c) 2026 YagirProtect.
// Licensed under GPL-3.0-only with the JetBrains IDE Linking Exception.
// See LICENSE and LICENSE-EXCEPTION in the project root.

package dev.yaro.rainbowbraces.editor

import dev.yaro.rainbowbraces.RainbowSettingsConfigurable
import dev.yaro.rainbowbraces.settings.RainbowSettingsService
import java.awt.Color
import kotlin.math.roundToInt

class RainbowSettingsUiTest : RainbowEditorTestCase() {
    fun testSettingsApplyAndResetUpdateOpenEditors() {
        configure("    {\n        body();\n    }")
        val configurable = RainbowSettingsConfigurable()
        try {
            fun checkBoxes(container: java.awt.Container): List<javax.swing.JCheckBox> =
                container.components.flatMap {
                    when (it) {
                        is javax.swing.JCheckBox -> listOf(it)
                        is java.awt.Container -> checkBoxes(it)
                        else -> emptyList()
                    }
                }
            val checkBox = checkBoxes(configurable.createComponent()).single { it.text == "Color indent guides" }
            assertTrue(checkBox.isSelected)
            assertFalse(configurable.isModified)
            checkBox.isSelected = false
            assertTrue(configurable.isModified)
            configurable.reset()
            assertTrue(checkBox.isSelected)
            assertFalse(configurable.isModified)
            checkBox.isSelected = false
            configurable.apply()
            assertFalse(RainbowSettingsService.getInstance().preferences().indentGuidesEnabled)
            assertFalse(configurable.isModified)
            assertEmpty(guides())
        } finally {
            configurable.disposeUIResources()
        }
    }

    fun testTransparencyControlsApplyResetAndUpdateOpenEditor() {
        configure("    {\n        body();\n    }")
        val editor = myFixture.editor
        val service = RainbowSettingsService.getInstance()
        val configurable = RainbowSettingsConfigurable()
        try {
            fun sliders(container: java.awt.Container): List<javax.swing.JSlider> =
                container.components.flatMap {
                    when (it) {
                        is javax.swing.JSlider -> listOf(it)
                        is java.awt.Container -> sliders(it)
                        else -> emptyList()
                    }
                }
            val controls = sliders(configurable.createComponent()).associateBy { it.name }
            val active = controls.getValue("activeIndentGuideTransparency")
            val inactive = controls.getValue("inactiveIndentGuideTransparency")
            assertEquals(0, active.value)
            assertEquals(75, inactive.value)
            active.value = 40
            inactive.value = 90
            assertTrue(configurable.isModified)
            configurable.reset()
            assertEquals(0, active.value)
            assertEquals(75, inactive.value)
            assertFalse(configurable.isModified)
            active.value = 40
            inactive.value = 90
            configurable.apply()
            assertEquals(40, service.preferences().activeIndentGuideTransparency)
            assertEquals(90, service.preferences().inactiveIndentGuideTransparency)
            assertFalse(configurable.isModified)
            val color = service.activeColors().first()
            val background = editor.colorsScheme.defaultBackground
            val expectedActive = Color(
                (color.red * 0.6 + background.red * 0.4).roundToInt(),
                (color.green * 0.6 + background.green * 0.4).roundToInt(),
                (color.blue * 0.6 + background.blue * 0.4).roundToInt()
            )
            editor.caretModel.moveToOffset(4)
            val y = editor.offsetToXY(editor.document.getLineStartOffset(1)).y + editor.lineHeight / 2
            assertEquals(expectedActive.rgb, render(guides().single()).getRGB(guideX(4), y))
            inactive.value = 100
            configurable.apply()
            editor.caretModel.moveToOffset(0)
            assertTrue(isBlank(render(guides().single())))
            editor.caretModel.moveToOffset(4)
            assertFalse(isBlank(render(guides().single())))
            active.value = 100
            inactive.value = 0
            configurable.apply()
            assertTrue(isBlank(render(guides().single())))
            editor.caretModel.moveToOffset(editor.document.getLineStartOffset(1) + 9)
            assertFalse(isBlank(render(guides().single())))
            editor.selectionModel.setSelection(4, 5)
            assertTrue(isBlank(render(guides().single())))
        } finally {
            configurable.disposeUIResources()
        }
    }
}
