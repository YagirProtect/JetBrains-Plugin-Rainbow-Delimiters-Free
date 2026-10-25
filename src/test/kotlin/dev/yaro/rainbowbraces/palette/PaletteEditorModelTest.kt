// Copyright (c) 2026 YagirProtect.
// Licensed under GPL-3.0-only with the JetBrains IDE Linking Exception.
// See LICENSE and LICENSE-EXCEPTION in the project root.

package dev.yaro.rainbowbraces.palette

import dev.yaro.rainbowbraces.settings.RainbowPreferences
import dev.yaro.rainbowbraces.settings.RainbowSettingsState
import junit.framework.TestCase

class PaletteEditorModelTest : TestCase() {
    fun testEditingCopyDoesNotChangeOriginalPreferences() {
        val original = RainbowPreferences()
        val model = PaletteEditorModel(original)
        model.duplicateActive()
        val id = model.activePaletteId
        model.rename(id, "My palette")
        model.setColor(id, 0, 0x123456)
        model.addColor(id)
        assertEquals(RainbowBuiltInPalettes.all.size, original.palettes.size)
        assertEquals("My palette", model.palettes.last().name)
        assertEquals(0x123456, model.palettes.last().lightColors.first())
        assertEquals(original.palettes.first().lightColors.size + 1, model.palettes.last().lightColors.size)
        model.reset(original)
        assertEquals(original.palettes, model.palettes)
        assertEquals(original.activePaletteId, model.activePaletteId)
    }

    fun testBuiltInIdentityAndMinimumColorCountAreProtected() {
        val model = PaletteEditorModel()
        val builtIn = model.palettes.first()
        model.rename(builtIn.id, "Changed")
        model.delete(builtIn.id)
        assertEquals(builtIn, model.palettes.first())
        model.duplicateActive()
        val id = model.activePaletteId
        repeat(20) { model.removeColor(id, 0) }
        assertEquals(RainbowSettingsState.MIN_COLOR_COUNT, model.palettes.last().lightColors.size)
        assertEquals(RainbowSettingsState.MIN_COLOR_COUNT, model.palettes.last().darkColors.size)
        model.delete(id)
        assertTrue(model.palettes.any { it.id == model.activePaletteId })
    }
}
