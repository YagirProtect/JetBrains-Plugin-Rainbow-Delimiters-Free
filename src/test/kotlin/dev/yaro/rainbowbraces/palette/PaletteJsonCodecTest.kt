package dev.yaro.rainbowbraces.palette

import junit.framework.TestCase

class PaletteJsonCodecTest : TestCase() {
    fun testLegacyJsonAcceptsReorderedFieldsAndEscapedNames() {
        val palettes = PaletteJsonCodec.decode("""{
            "palettes": [{"colors":["#112233","445566"], "name":"Quotes \" and \\ and \n", "id":"custom", "builtInId":""}],
            "activePaletteId":"custom", "version":1
        }""")
        assertEquals(1, palettes.size)
        assertEquals("Quotes \" and \\ and \n", palettes.single().name)
        assertEquals(listOf(0x112233, 0x445566), palettes.single().lightColors)
        assertEquals(palettes.single().lightColors, palettes.single().darkColors)
    }

    fun testRoundTripPreservesLightAndDarkColors() {
        val palette = RainbowPalette("custom", "Name: | \" \\ \n", lightColors = listOf(0x112233, 0x445566), darkColors = listOf(0xAABBCC, 0xDDEEFF))
        assertEquals(listOf(palette), PaletteJsonCodec.decode(PaletteJsonCodec.encode("custom", listOf(palette))))
    }

    fun testInvalidColorDoesNotSilentlyProduceAnotherPalette() {
        try {
            PaletteJsonCodec.decode("""{"palettes":[{"id":"bad", "colors":["#GGGGGG", "#123456"]}]}""")
            fail("Malformed colors should be reported")
        } catch (_: IllegalArgumentException) {
            // The settings UI reports the import error without changing its working model.
        }
    }
}
