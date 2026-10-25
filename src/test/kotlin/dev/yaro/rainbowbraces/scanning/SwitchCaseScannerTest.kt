package dev.yaro.rainbowbraces.scanning

import junit.framework.TestCase

class SwitchCaseScannerTest : TestCase() {
    private fun sections(text: String) = DelimiterScanner().scanText(text, 8, includeSwitchSections = true).caseSections

    fun testWhenLabelsAndDefaultEndAtTheNextSection() {
        val text = """
            switch (rpc) {
                case DeathRpc:
                    ChangeGameState(EGameState.Death);
                    break;
                case AssignCharacterRpc when gameState == EGameState.Death:
                    ChangeGameState(EGameState.Game);
                    break;
                default:
                    return;
            }
        """.trimIndent()
        val result = sections(text)
        assertEquals(3, result.size)
        result.forEachIndexed { index, section ->
            assertEquals(':', text[section.bodyOffset - 1])
            assertEquals(result.getOrNull(index + 1)?.labelOffset ?: text.lastIndexOf('}'), section.endOffset)
        }
        assertEquals(text.indexOf("case Assign"), result[1].labelOffset)
    }

    fun testStackedLabelsAndExplicitBraceBlocksDoNotAddExtraGuides() {
        val text = "switch (x) { case 0:\ncase 1:\nRun(); break;\ncase 2: { Run(); break; }\ndefault:\nreturn; }"
        val result = sections(text)
        assertEquals(listOf(text.indexOf("case 1"), text.indexOf("default:")), result.map { it.labelOffset })
        assertEquals(text.indexOf("case 2"), result[0].endOffset)
    }

    fun testNestedSwitchesKeepTheirOwnSectionBoundaries() {
        val text = "switch (outer) { case 0:\nswitch (inner) { case 1:\nRun(); break; default:\nreturn; }\nbreak; default:\nStop(); }"
        val result = sections(text)
        assertEquals(4, result.size)
        assertEquals(text.lastIndexOf("default:"), result[0].endOffset)
        assertEquals(text.indexOf("default:"), result[1].endOffset)
        assertEquals(text.indexOf('}', text.indexOf("default:")), result[2].endOffset)
        assertEquals(text.lastIndexOf('}'), result[3].endOffset)
    }

    fun testCppScopeResolutionAndCSharpPatternAndConditionalColons() {
        val text = "switch (x) { case MyEnum::First:\nOne(); break; case Node { Count: 1 } n when flag ? A() : B():\nTwo(); break; case var v when v?.Ready ?? false:\nThree(); break; default:\nStop(); }"
        val result = sections(text)
        assertEquals(4, result.size)
        assertEquals(text.indexOf("First:") + "First:".length, result[0].bodyOffset)
        assertEquals(text.indexOf("B():") + "B():".length, result[1].bodyOffset)
        assertEquals(text.indexOf("false:") + "false:".length, result[2].bodyOffset)
    }

    fun testCommentsStringsSwitchExpressionsAndGotoLabelsAreIgnored() {
        val text = "// switch (fake) { case 0: fake(); }\n\"switch (fake) { default: fake(); }\";\nvar y = x switch { 0 => 1, _ => 2 };\nswitch (x) { case ':':\n/* case fake: */ goto case 1;\ngoto default;\ncase 1:\nRun();\ndefault:\nStop(); }"
        val result = sections(text)
        assertEquals(3, result.size)
        assertEquals(text.indexOf("case ':'"), result[0].labelOffset)
        assertEquals(text.lastIndexOf("case 1:"), result[0].endOffset)
        assertEquals(text.lastIndexOf("default:"), result[1].endOffset)
    }

    fun testLabelsOutsideSwitchAndIncompleteSwitchesAreIgnored() {
        assertTrue(sections("case 0:\nRun();\ndefault:\nStop();").isEmpty())
        assertTrue(sections("switch (x) { case 0:\nRun();").isEmpty())
        assertTrue(sections("switch (x) { case unfinished;\nRun(); }").isEmpty())
    }
}
