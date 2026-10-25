package dev.yaro.rainbowbraces.palette

import dev.yaro.rainbowbraces.settings.RainbowPreferences

/**
 * Swing-independent working copy for unsaved palette edits. Individual palettes are
 * immutable, so operations replace values in a private list rather than mutating
 * objects also held by the application service. The exposed list is a detached,
 * unmodifiable snapshot suitable for isModified comparisons and eventual Apply.
 */
internal class PaletteEditorModel(preferences: RainbowPreferences = RainbowPreferences()) {
    private val working = preferences.palettes.toMutableList()
    var activePaletteId: String = preferences.activePaletteId
        private set
    val palettes: List<RainbowPalette> get() = java.util.List.copyOf(working)

    fun reset(preferences: RainbowPreferences) {
        working.clear()
        working.addAll(preferences.palettes)
        activePaletteId = preferences.activePaletteId
    }

    fun select(id: String) {
        if (working.any { it.id == id }) activePaletteId = id
    }

    fun duplicateActive() {
        // A duplicate is a custom palette, even when copied from a built-in source.
        val active = working.first { it.id == activePaletteId }
        val copy = active.copy(id = "custom-${java.util.UUID.randomUUID()}", name = "${active.name} copy", builtInId = "")
        working.add(copy)
        activePaletteId = copy.id
    }

    fun rename(id: String, name: String) = edit(id) {
        if (it.isBuiltIn()) it else it.copy(name = name.ifBlank { "Custom palette" })
    }

    fun addColor(id: String) = edit(id) {
        val color = it.lightColors.last()
        it.copy(lightColors = it.lightColors + color, darkColors = it.darkColors + color)
    }

    fun removeColor(id: String, index: Int) = edit(id) {
        if (it.lightColors.size <= RainbowPalette.MIN_COLOR_COUNT || index !in it.lightColors.indices) it
        else it.copy(lightColors = it.lightColors.filterIndexed { i, _ -> i != index }, darkColors = it.darkColors.filterIndexed { i, _ -> i != index })
    }

    fun setColor(id: String, index: Int, rgb: Int) = edit(id) {
        // A manual picker edit intentionally sets the same RGB for both themes.
        // Untouched built-in entries retain their separate light/dark variants.
        if (index !in it.lightColors.indices) it else it.copy(
            lightColors = it.lightColors.mapIndexed { i, color -> if (i == index) rgb and 0xFFFFFF else color },
            darkColors = it.darkColors.mapIndexed { i, color -> if (i == index) rgb and 0xFFFFFF else color }
        )
    }

    fun resetBuiltIn(id: String) = edit(id) { palette ->
        RainbowBuiltInPalettes.find(palette.builtInId)?.let {
            palette.copy(lightColors = it.lightColors, darkColors = it.darkColors)
        } ?: palette
    }

    fun delete(id: String) {
        val index = working.indexOfFirst { it.id == id }
        if (index < 0 || working[index].isBuiltIn()) return
        working.removeAt(index)
        if (activePaletteId == id) activePaletteId = working.getOrNull(index.coerceAtMost(working.lastIndex))?.id
            ?: RainbowBuiltInPalettes.default.id
    }

    fun importPalettes(imported: List<RainbowPalette>) {
        // Import replaces the custom palette set rather than appending duplicates.
        // Built-ins must always survive: prefer imported values, then current edits,
        // then catalog defaults. Selection survives only if its ID is still present.
        val merged = RainbowBuiltInPalettes.all.map { builtIn ->
            (imported.firstOrNull { it.id == builtIn.id } ?: working.firstOrNull { it.id == builtIn.id }
                ?: builtIn.toPalette()).copy(builtInId = builtIn.id)
        } + imported.filter { RainbowBuiltInPalettes.find(it.id) == null }
        working.clear()
        working.addAll(merged)
        if (working.none { it.id == activePaletteId }) activePaletteId = working.first().id
    }

    private fun edit(id: String, transform: (RainbowPalette) -> RainbowPalette) {
        val index = working.indexOfFirst { it.id == id }
        if (index >= 0) working[index] = transform(working[index])
    }
}
