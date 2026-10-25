package dev.yaro.rainbowbraces.palette


/**
 * Immutable palette identity and unadjusted 24-bit RGB values for both IDE themes.
 * Display adjustments belong to PaletteColorAdjustment, never to this stored data.
 * Defensive copies prevent callers from mutating settings through retained lists.
 * Equal theme lengths keep nesting indices valid across a theme change.
 * [builtInId] retains the reset source even after built-in colors have been edited;
 * custom duplicates clear that identity and receive their own persistent [id].
 */
class RainbowPalette(
    val id: String,
    val name: String,
    val builtInId: String = "",
    lightColors: Collection<Int>,
    darkColors: Collection<Int> = lightColors
) {
    val lightColors: List<Int> = java.util.List.copyOf(lightColors)
    val darkColors: List<Int> = java.util.List.copyOf(darkColors)

    init {
        require(id.isNotBlank()) { "A palette must have an id" }
        require(this.lightColors.size >= MIN_COLOR_COUNT) { "A palette needs at least two colors" }
        require(this.darkColors.size == this.lightColors.size) { "Light and dark palettes must have equal sizes" }
    }

    fun isBuiltIn(): Boolean = builtInId.isNotBlank()

    fun copy(
        id: String = this.id,
        name: String = this.name,
        builtInId: String = this.builtInId,
        lightColors: Collection<Int> = this.lightColors,
        darkColors: Collection<Int> = this.darkColors
    ) = RainbowPalette(id, name, builtInId, lightColors, darkColors)

    override fun equals(other: Any?): Boolean = other is RainbowPalette &&
        id == other.id && name == other.name && builtInId == other.builtInId &&
        lightColors == other.lightColors && darkColors == other.darkColors

    override fun hashCode(): Int = java.util.Objects.hash(id, name, builtInId, lightColors, darkColors)

    companion object {
        const val MIN_COLOR_COUNT = 2
    }
}

/** Factory definition used to create or reset editable palettes without modifying the catalog. */
class RainbowBuiltInPalette(
    val id: String,
    val displayName: String,
    lightColors: Collection<Int>,
    darkColors: Collection<Int>
) {
    val lightColors: List<Int> = java.util.List.copyOf(lightColors)
    val darkColors: List<Int> = java.util.List.copyOf(darkColors)
    fun toPalette() = RainbowPalette(id, displayName, id, lightColors, darkColors)
}

/** Stable IDs are persisted in XML/JSON; display names may change independently. */
object RainbowBuiltInPalettes {
    val all: List<RainbowBuiltInPalette> = java.util.List.copyOf(listOf(
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
    ))

    val default: RainbowBuiltInPalette = all.first()

    fun find(id: String?): RainbowBuiltInPalette? = all.firstOrNull { it.id == id }

    fun fromLegacyName(name: String?): RainbowBuiltInPalette =
        // Old settings stored enum names rather than the stable palette IDs used now.
        when (name) {
            "VIVID" -> all[1]
            "PASTEL" -> all[2]
            "COLORBLIND_FRIENDLY" -> all[3]
            else -> default
        }
}
