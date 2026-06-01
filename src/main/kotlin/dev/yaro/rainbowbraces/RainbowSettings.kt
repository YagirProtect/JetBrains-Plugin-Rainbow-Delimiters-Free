package dev.yaro.rainbowbraces

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.ui.JBColor
import java.awt.Color
import java.util.UUID

class RainbowBuiltInPalette(
    val id: String,
    val displayName: String,
    val lightColors: List<Int>,
    val darkColors: List<Int>
)

object RainbowBuiltInPalettes {
    val all = listOf(
        RainbowBuiltInPalette(
            "default",
            "Default",
            listOf(0xC62828, 0xAD1457, 0x6A1B9A, 0x283593, 0x1565C0, 0x00695C, 0x2E7D32, 0xF9A825),
            listOf(0xFF6B6B, 0xFF4D9D, 0xB388FF, 0x82B1FF, 0x4FC3F7, 0x64FFDA, 0xB9F6CA, 0xFFE082)
        ),
        RainbowBuiltInPalette(
            "vivid",
            "Vivid",
            listOf(0xD32F2F, 0xC2185B, 0x7B1FA2, 0x303F9F, 0x0288D1, 0x00796B, 0x689F38, 0xFBC02D),
            listOf(0xFF5252, 0xFF4081, 0xE040FB, 0x536DFE, 0x40C4FF, 0x1DE9B6, 0xB2FF59, 0xFFFF00)
        ),
        RainbowBuiltInPalette(
            "pastel",
            "Pastel",
            listOf(0xB85C5C, 0xB565A7, 0x8E6BBE, 0x6277B8, 0x4C8DAA, 0x4D9A86, 0x80A653, 0xC59B35),
            listOf(0xFF9A9A, 0xFF9DCE, 0xD2B6FF, 0xA8BAFF, 0x9DE6FF, 0x9CF5DE, 0xD6F7A3, 0xFFE7A3)
        ),
        RainbowBuiltInPalette(
            "colorblind",
            "Colorblind-friendly",
            listOf(0x0072B2, 0xD55E00, 0x009E73, 0xCC79A7, 0xE69F00, 0x56B4E9, 0xF0E442, 0x000000),
            listOf(0x56B4E9, 0xE69F00, 0x009E73, 0xCC79A7, 0xF0E442, 0x0072B2, 0xD55E00, 0xFFFFFF)
        )
    )

    val default: RainbowBuiltInPalette = all.first()

    fun find(id: String?): RainbowBuiltInPalette? = all.firstOrNull { it.id == id }

    fun fromLegacyName(name: String?): RainbowBuiltInPalette =
        when (name) {
            "VIVID" -> all[1]
            "PASTEL" -> all[2]
            "COLORBLIND_FRIENDLY" -> all[3]
            else -> default
        }
}

object RainbowDefaultFileTypes {
    val extensions = listOf(
        "rs", "cs",
        "java", "kt", "kts",
        "c", "h", "cc", "cpp", "cxx", "hpp", "hxx",
        "js", "ts", "jsx", "tsx",
        "py", "go", "swift", "lua", "json", "hlsl", "shader", "php"
    )
}

class RainbowPaletteState {
    var id: String = ""
    var name: String = ""
    var builtInId: String = ""
    var lightColors: MutableList<Int> = arrayListOf()
    var darkColors: MutableList<Int> = arrayListOf()

    fun isBuiltIn(): Boolean = builtInId.isNotBlank()
}

class RainbowSettingsState {
    var enabled: Boolean = true
    var pairEmphasisEnabled: Boolean = true
    var activePaletteId: String = RainbowBuiltInPalettes.default.id

    // Kept for migration from versions where the user could limit active colors separately.
    var colorCount: Int = DEFAULT_COLOR_COUNT

    // Kept for migration from version 1.3.0-dev where the selected palette was an enum name.
    var paletteName: String = ""
    var palettes: MutableList<RainbowPaletteState> = arrayListOf()
    var enabledExtensions: MutableList<String> = ArrayList(RainbowDefaultFileTypes.extensions)

    companion object {
        const val MIN_COLOR_COUNT = 2
        const val DEFAULT_COLOR_COUNT = 8
    }
}

@Service(Service.Level.APP)
@State(
    name = "RainbowDelimitersSettings",
    storages = [Storage("rainbowDelimiters.xml")]
)
class RainbowSettingsService : PersistentStateComponent<RainbowSettingsState> {
    private var state = RainbowSettingsState()

    var modificationCount: Long = 0
        private set

    override fun getState(): RainbowSettingsState {
        normalize()
        return state
    }

    override fun loadState(state: RainbowSettingsState) {
        this.state = state
        normalize()
        modificationCount++
    }

    fun palettes(): MutableList<RainbowPaletteState> {
        normalize()
        return state.palettes
    }

    fun activePalette(): RainbowPaletteState {
        normalize()
        return state.palettes.firstOrNull { it.id == state.activePaletteId } ?: state.palettes.first()
    }

    fun activeColors(): Array<Color> {
        val palette = activePalette()
        return Array(palette.lightColors.size) { index ->
            JBColor(Color(palette.lightColors[index]), Color(palette.darkColors[index]))
        }
    }

    fun isEnabledForExtension(extension: String?): Boolean {
        if (!state.enabled || extension == null) return false
        normalize()
        return extension.lowercase() in state.enabledExtensions
    }

    fun isPairEmphasisEnabled(): Boolean = state.enabled && state.pairEmphasisEnabled

    fun update(
        enabled: Boolean,
        pairEmphasisEnabled: Boolean,
        activePaletteId: String,
        palettes: List<RainbowPaletteState>,
        enabledExtensions: Collection<String>
    ) {
        state.enabled = enabled
        state.pairEmphasisEnabled = pairEmphasisEnabled
        state.activePaletteId = activePaletteId
        state.palettes = palettes.mapTo(arrayListOf()) { it.copyPalette() }
        state.enabledExtensions = normalizeExtensions(enabledExtensions)
        normalize()
        modificationCount++
    }

    private fun normalize() {
        migrateLegacyPalette()
        ensureBuiltInPalettes()

        if (state.palettes.isEmpty()) {
            state.palettes = RainbowBuiltInPalettes.all.mapTo(arrayListOf()) { it.toState() }
        }

        val activeExists = state.palettes.any { it.id == state.activePaletteId }
        if (!activeExists) {
            state.activePaletteId = state.palettes.first().id
        }

        state.palettes.forEach { palette ->
            if (palette.lightColors.size < RainbowSettingsState.MIN_COLOR_COUNT) {
                val fallback = RainbowBuiltInPalettes.default.lightColors
                while (palette.lightColors.size < RainbowSettingsState.MIN_COLOR_COUNT) {
                    palette.lightColors.add(fallback[palette.lightColors.size])
                }
            }
            if (palette.darkColors.size != palette.lightColors.size) {
                palette.darkColors = ArrayList(palette.lightColors)
            }
        }

        state.enabledExtensions = normalizeExtensions(state.enabledExtensions)
        if (state.enabledExtensions.isEmpty()) {
            state.enabledExtensions = ArrayList(RainbowDefaultFileTypes.extensions)
        }
    }

    private fun migrateLegacyPalette() {
        if (state.paletteName.isBlank()) return
        val legacy = RainbowBuiltInPalettes.fromLegacyName(state.paletteName)
        state.activePaletteId = legacy.id
        state.paletteName = ""
    }

    private fun ensureBuiltInPalettes() {
        val byId = state.palettes.associateBy { it.id }
        val complete = ArrayList<RainbowPaletteState>()

        RainbowBuiltInPalettes.all.forEach { builtIn ->
            complete.add(byId[builtIn.id] ?: builtIn.toState())
        }

        state.palettes
            .filterNot { RainbowBuiltInPalettes.find(it.id) != null }
            .forEach(complete::add)

        state.palettes = complete
    }

    companion object {
        fun getInstance(): RainbowSettingsService =
            ApplicationManager.getApplication().getService(RainbowSettingsService::class.java)
    }
}

private fun normalizeExtensions(extensions: Collection<String>): MutableList<String> =
    extensions
        .map { it.trim().removePrefix(".").lowercase() }
        .filter { it.isNotBlank() }
        .distinct()
        .sorted()
        .toMutableList()

fun RainbowBuiltInPalette.toState(): RainbowPaletteState =
    RainbowPaletteState().also {
        it.id = id
        it.name = displayName
        it.builtInId = id
        it.lightColors = ArrayList(lightColors)
        it.darkColors = ArrayList(darkColors)
    }

fun RainbowPaletteState.copyPalette(): RainbowPaletteState =
    RainbowPaletteState().also {
        it.id = id
        it.name = name
        it.builtInId = builtInId
        it.lightColors = ArrayList(lightColors)
        it.darkColors = ArrayList(darkColors)
    }

fun RainbowPaletteState.copyAsCustom(): RainbowPaletteState =
    copyPalette().also {
        it.id = "custom-${UUID.randomUUID()}"
        it.name = "$name copy"
        it.builtInId = ""
    }
