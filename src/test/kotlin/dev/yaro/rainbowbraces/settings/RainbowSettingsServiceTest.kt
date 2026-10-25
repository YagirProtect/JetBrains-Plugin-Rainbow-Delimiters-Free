package dev.yaro.rainbowbraces.settings

import dev.yaro.rainbowbraces.palette.RainbowPalette
import java.awt.Color
import junit.framework.TestCase

class RainbowSettingsServiceTest : TestCase() {
    fun testNewSettingsRecordTheCurrentBuildWithoutChangingPreferencesOnRead() {
        val service = RainbowSettingsService()
        val original = service.preferences()
        val revision = service.modificationCount
        assertTrue(PluginBuildInfo.version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+.*")))
        assertNull(service.loadedFromPluginVersion)
        assertEquals(PluginBuildInfo.version, service.state.lastSavedPluginVersion)
        assertSame(original, service.preferences())
        assertEquals(revision, service.modificationCount)
    }

    fun testOldSettingsKeepUnknownOriginAndPreserveCustomExtensions() {
        val legacy = com.intellij.util.xmlb.XmlSerializer.deserialize(org.jdom.Element("state"), RainbowSettingsState::class.java)
        assertEquals("", legacy.lastSavedPluginVersion)
        legacy.enabledExtensions = arrayListOf("cs", "rs")
        val service = RainbowSettingsService()
        service.loadState(legacy)
        assertNull(service.loadedFromPluginVersion)
        assertEquals(listOf("cs", "rs"), service.preferences().enabledExtensions)
        assertEquals(PluginBuildInfo.version, service.state.lastSavedPluginVersion)
        assertEquals("", legacy.lastSavedPluginVersion)
    }

    fun testPreviousVersionRemainsAvailableAndTheCurrentVersionPersistsOnSave() {
        val incoming = RainbowSettingsState().apply {
            lastSavedPluginVersion = "1.7.5"
            enabledExtensions = arrayListOf("cs", "rs")
            colorBrightness = 70
        }
        val service = RainbowSettingsService()
        service.loadState(incoming)
        assertEquals("1.7.5", service.loadedFromPluginVersion)
        val saved = service.state
        assertEquals(PluginBuildInfo.version, saved.lastSavedPluginVersion)
        assertEquals("1.7.5", service.loadedFromPluginVersion)
        assertEquals("1.7.5", incoming.lastSavedPluginVersion)
        val xml = com.intellij.util.xmlb.XmlSerializer.serialize(saved)
        val restored = com.intellij.util.xmlb.XmlSerializer.deserialize(xml, RainbowSettingsState::class.java)
        val nextSession = RainbowSettingsService()
        nextSession.loadState(restored)
        assertEquals(PluginBuildInfo.version, nextSession.loadedFromPluginVersion)
        assertEquals(service.preferences(), nextSession.preferences())
        saved.lastSavedPluginVersion = "changed externally"
        assertEquals(PluginBuildInfo.version, service.state.lastSavedPluginVersion)
    }

    fun testColorAdjustmentSettingsPersistValidateAndKeepTheRawPalette() {
        val service = RainbowSettingsService()
        val originalPalettes = service.palettes()
        service.updatePreferences(service.preferences().copy(colorSaturation = 35, colorBrightness = 70))
        val xml = com.intellij.util.xmlb.XmlSerializer.serialize(service.state)
        val restored = com.intellij.util.xmlb.XmlSerializer.deserialize(xml, RainbowSettingsState::class.java)
        service.loadState(restored)
        assertEquals(35, service.preferences().colorSaturation)
        assertEquals(70, service.preferences().colorBrightness)
        assertEquals(originalPalettes, service.palettes())
        restored.colorSaturation = -10
        restored.colorBrightness = 150
        service.loadState(restored)
        assertEquals(0, service.preferences().colorSaturation)
        assertEquals(100, service.preferences().colorBrightness)
        val legacy = com.intellij.util.xmlb.XmlSerializer.deserialize(org.jdom.Element("state"), RainbowSettingsState::class.java)
        assertEquals(100, legacy.colorSaturation)
        assertEquals(100, legacy.colorBrightness)
    }

    fun testColorAdjustmentsFollowTheSelectedCustomPaletteWithoutAccumulating() {
        val service = RainbowSettingsService()
        val custom = RainbowPalette("adjusted", "Adjusted", lightColors = listOf(0x336699, 0xAA5533))
        service.updatePreferences(service.preferences().copy(palettes = service.palettes() + custom, activePaletteId = custom.id))
        assertEquals(Color(0x336699), service.activeColors().first())
        service.updatePreferences(service.preferences().copy(colorSaturation = 0, colorBrightness = 50))
        assertEquals(Color(0x4D4D4D), service.activeColors().first())
        service.updatePreferences(service.preferences().copy(colorSaturation = 100, colorBrightness = 100))
        assertEquals(Color(0x336699), service.activeColors().first())
        assertEquals(custom, service.activePalette())
    }

    fun testNewCppAndGlslDefaultsDoNotExpandSavedExtensionChoices() {
        val service = RainbowSettingsService()
        for (extension in listOf("hh", "ixx", "cppm", "c++m", "glsl", "frag", "comp")) {
            assertTrue(service.isEnabledForExtension(extension))
        }
        val saved = service.state.apply { enabledExtensions = arrayListOf("cs", "rs") }
        service.loadState(saved)
        assertEquals(listOf("cs", "rs"), service.preferences().enabledExtensions)
    }

    fun testSerializationBeansCannotMutateLiveSettings() {
        val service = RainbowSettingsService()
        val original = service.preferences()
        val bean = service.state
        bean.enabled = false
        bean.enabledExtensions.clear()
        bean.palettes.first().lightColors[0] = 0
        bean.palettes.clear()
        assertEquals(original, service.preferences())
        assertTrue(service.enabled)
    }

    fun testInputCollectionsAreCopiedAndGetterCollectionsAreUnmodifiable() {
        val service = RainbowSettingsService()
        val colors = mutableListOf(0x112233, 0x445566)
        val custom = RainbowPalette("custom", "Custom", lightColors = colors)
        val palettes = (service.palettes() + custom).toMutableList()
        val extensions = mutableListOf(".KT", "kt")
        service.updatePreferences(service.preferences().copy(activePaletteId = "custom", palettes = palettes, enabledExtensions = extensions))
        colors[0] = 0
        palettes.clear()
        extensions.clear()
        assertEquals(0x112233, service.activePalette().lightColors.first())
        assertEquals(listOf("kt"), service.preferences().enabledExtensions)
        assertTrue(service.isEnabledForExtension("kt"))
        expectUnmodifiable { (service.palettes() as MutableList<RainbowPalette>).clear() }
        expectUnmodifiable { (service.activePalette().lightColors as MutableList<Int>)[0] = 0 }
        expectUnmodifiable { (service.preferences().enabledExtensions as MutableList<String>).clear() }
        val activeColors = service.activeColors()
        val originalColor = activeColors.first()
        activeColors[0] = Color.BLACK
        assertEquals(originalColor, service.activeColors().first())
    }

    fun testLoadingStateDoesNotRetainInputBeans() {
        val service = RainbowSettingsService()
        val bean = service.state
        bean.activeIndentGuideTransparency = 35
        service.loadState(bean)
        val loaded = service.preferences()
        bean.activeIndentGuideTransparency = 100
        bean.palettes.first().name = "Changed elsewhere"
        bean.palettes.first().lightColors.clear()
        assertEquals(loaded, service.preferences())
        assertEquals(35, service.indentGuideTransparency(true))
    }

    fun testReadsDoNotNormalizeOrModifySettings() {
        val service = RainbowSettingsService()
        val preferences = service.preferences()
        val revision = service.modificationCount
        repeat(3) {
            service.state
            service.activeColors()
            service.activePalette()
            service.isEnabledForExtension("rs")
        }
        assertSame(preferences, service.preferences())
        assertEquals(revision, service.modificationCount)
    }

    fun testLegacySettingsMigrateWithoutLosingPaletteColors() {
        val bean = RainbowSettingsState().apply {
            paletteName = "PASTEL"
            activeIndentGuideTransparency = -1
            inactiveIndentGuideTransparency = 120
            enabledExtensions = arrayListOf(".KT", "kt", " JAVA ")
            palettes.add(RainbowPaletteState().apply {
                id = "pastel"
                name = "Pastel"
                lightColors = arrayListOf(0x123456, 0x654321)
                darkColors = arrayListOf(0xAABBCC, 0xDDEEFF)
            })
        }
        val service = RainbowSettingsService()
        service.loadState(bean)
        assertEquals("pastel", service.activePalette().id)
        assertTrue(service.activePalette().isBuiltIn())
        assertEquals(listOf(0x123456, 0x654321), service.activePalette().lightColors)
        assertEquals(listOf(0xAABBCC, 0xDDEEFF), service.activePalette().darkColors)
        assertEquals(listOf("java", "kt"), service.preferences().enabledExtensions)
        assertEquals(0, service.indentGuideTransparency(true))
        assertEquals(100, service.indentGuideTransparency(false))
        val restored = RainbowSettingsService()
        restored.loadState(com.intellij.util.xmlb.XmlSerializer.deserialize(
            com.intellij.util.xmlb.XmlSerializer.serialize(service.state), RainbowSettingsState::class.java
        ))
        assertEquals(service.preferences(), restored.preferences())
    }

    private fun expectUnmodifiable(action: () -> Unit) {
        try {
            action()
            fail("A getter exposed a mutable collection")
        } catch (_: UnsupportedOperationException) {
            // Expected: mutation must not be possible through a getter.
        }
    }

    fun testTransparencySettingsPersistAndValidateStoredValues() {
        val service = RainbowSettingsService()
        service.updatePreferences(service.preferences().copy(activeIndentGuideTransparency = 35))
        service.updatePreferences(service.preferences().copy(inactiveIndentGuideTransparency = 85))
        val xml = com.intellij.util.xmlb.XmlSerializer.serialize(service.state)
        val restored = com.intellij.util.xmlb.XmlSerializer.deserialize(xml, RainbowSettingsState::class.java)
        service.loadState(restored)
        assertEquals(35, service.indentGuideTransparency(true))
        assertEquals(85, service.indentGuideTransparency(false))
        restored.activeIndentGuideTransparency = -10
        restored.inactiveIndentGuideTransparency = 110
        service.loadState(restored)
        assertEquals(0, service.preferences().activeIndentGuideTransparency)
        assertEquals(100, service.preferences().inactiveIndentGuideTransparency)
        val legacy = com.intellij.util.xmlb.XmlSerializer.deserialize(org.jdom.Element("state"), RainbowSettingsState::class.java)
        assertEquals(0, legacy.activeIndentGuideTransparency)
        assertEquals(75, legacy.inactiveIndentGuideTransparency)
    }
}
