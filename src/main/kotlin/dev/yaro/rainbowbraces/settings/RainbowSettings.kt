package dev.yaro.rainbowbraces.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.ui.JBColor
import dev.yaro.rainbowbraces.palette.RainbowBuiltInPalettes
import dev.yaro.rainbowbraces.palette.RainbowPalette
import dev.yaro.rainbowbraces.palette.PaletteColorAdjustment
import java.awt.Color

/**
 * Application-wide owner of validated preferences and their derived display colors.
 * Mutable XML beans are converted to immutable domain values on load. The UI edits
 * a separate draft and publishes it through updatePreferences only when Apply runs.
 * Reads never normalize, migrate or save state; this makes repeated rendering reads
 * predictable and prevents opening the settings page from changing user preferences.
 */
@Service(Service.Level.APP)
@State(name = "RainbowDelimitersSettings", storages = [Storage("rainbowDelimiters.xml")])
class RainbowSettingsService : PersistentStateComponent<RainbowSettingsState> {
    private var current = RainbowPreferences().normalized()
    private var colors = paletteColors()
    private var loadedFromVersion: String? = null

    /** The incoming version remains available for migration decisions, even after state is read for saving. */
    val loadedFromPluginVersion: String? get() = loadedFromVersion

    var modificationCount: Long = 0
        private set

    val enabled: Boolean get() = current.enabled
    val pairEmphasisEnabled: Boolean get() = current.pairEmphasisEnabled
    val indentGuidesEnabled: Boolean get() = current.indentGuidesEnabled

    fun preferences(): RainbowPreferences = current
    fun palettes(): List<RainbowPalette> = current.palettes
    fun activePalette(): RainbowPalette = current.palettes.first { it.id == current.activePaletteId }
    // Arrays are mutable even when their elements are immutable. Return a copy so
    // rendering code cannot accidentally overwrite the shared derived-color cache.
    fun activeColors(): Array<Color> = colors.clone()

    fun isEnabledForExtension(extension: String?): Boolean =
        current.enabled && extension != null && extension.lowercase() in current.enabledExtensions

    fun isPairEmphasisEnabled(): Boolean = current.enabled && current.pairEmphasisEnabled
    fun isIndentGuidesEnabled(): Boolean = current.enabled && current.indentGuidesEnabled
    fun indentGuideTransparency(active: Boolean): Int =
        if (active) current.activeIndentGuideTransparency else current.inactiveIndentGuideTransparency

    fun updatePreferences(preferences: RainbowPreferences) {
        current = preferences.normalized()
        colors = paletteColors()
        modificationCount++
    }

    // Return a fresh serialization bean: even callers of getState cannot change live settings.
    override fun getState(): RainbowSettingsState = RainbowSettingsState().also {
        it.lastSavedPluginVersion = PluginBuildInfo.version
        it.enabled = current.enabled
        it.pairEmphasisEnabled = current.pairEmphasisEnabled
        it.indentGuidesEnabled = current.indentGuidesEnabled
        it.activeIndentGuideTransparency = current.activeIndentGuideTransparency
        it.inactiveIndentGuideTransparency = current.inactiveIndentGuideTransparency
        it.colorSaturation = current.colorSaturation
        it.colorBrightness = current.colorBrightness
        it.activePaletteId = current.activePaletteId
        it.palettes = current.palettes.mapTo(arrayListOf()) { palette -> palette.toState() }
        it.enabledExtensions = ArrayList(current.enabledExtensions)
    }

    override fun loadState(state: RainbowSettingsState) {
        // Missing metadata means an unknown legacy origin, not the current version.
        // Keep this origin separate from the version stamped on subsequent saves.
        loadedFromVersion = state.lastSavedPluginVersion.trim().takeIf { it.isNotEmpty() }
        // Future migrations must inspect the incoming version here, before converting
        // preferences. getState stamps the current build only on the detached output bean.
        updatePreferences(RainbowPreferences(
            enabled = state.enabled,
            pairEmphasisEnabled = state.pairEmphasisEnabled,
            indentGuidesEnabled = state.indentGuidesEnabled,
            activeIndentGuideTransparency = state.activeIndentGuideTransparency,
            inactiveIndentGuideTransparency = state.inactiveIndentGuideTransparency,
            colorSaturation = state.colorSaturation,
            colorBrightness = state.colorBrightness,
            activePaletteId = if (state.paletteName.isNotBlank()) RainbowBuiltInPalettes.fromLegacyName(state.paletteName).id else state.activePaletteId,
            palettes = state.palettes.map { it.toPalette() },
            enabledExtensions = state.enabledExtensions
        ))
    }

    private fun paletteColors(): Array<Color> {
        // Cache adjusted light/dark pairs, leaving raw palette values untouched.
        // JBColor resolves the appropriate variant when the IDE theme changes.
        val palette = activePalette()
        return Array(palette.lightColors.size) { index ->
            JBColor(
                PaletteColorAdjustment.adjust(palette.lightColors[index], current.colorSaturation, current.colorBrightness),
                PaletteColorAdjustment.adjust(palette.darkColors[index], current.colorSaturation, current.colorBrightness)
            )
        }
    }

    companion object {
        fun getInstance(): RainbowSettingsService = ApplicationManager.getApplication().getService(RainbowSettingsService::class.java)
    }
}
