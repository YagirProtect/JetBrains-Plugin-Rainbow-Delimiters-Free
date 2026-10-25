// Copyright (c) 2026 YagirProtect.
// Licensed under GPL-3.0-only with the JetBrains IDE Linking Exception.
// See LICENSE and LICENSE-EXCEPTION in the project root.

package dev.yaro.rainbowbraces.scanning

import junit.framework.TestCase

class DelimiterScannerTest : TestCase() {
    private val scanner = DelimiterScanner()

    fun testNestedPairsShareColorsAndIgnoreCommentsAndStrings() {
        val text = "{ /* [ ignored ] */ call(\"}\", [1]); // }\n}"
        val scan = scanner.scanText(text, 3)
        assertEquals(6, scan.marks.size)
        assertEquals(text.lastIndexOf('}'), scan.pairsByOffset.getValue(0).pairedOffset)
        scan.pairsByOffset.forEach { (offset, pair) ->
            assertEquals(offset, scan.pairsByOffset.getValue(pair.pairedOffset).pairedOffset)
            assertEquals(pair.colorIndex, scan.pairsByOffset.getValue(pair.pairedOffset).colorIndex)
        }
        assertTrue(scan.marks.all { it.colorIndex in 0..2 })
    }

    fun testFallbackHandlesVerbatimAndRawStrings() {
        val text = "{ @\"[\"\"}]\"; r##\"{[()]}\"##; [] }"
        val scan = scanner.scanText(text, 8)
        assertEquals(4, scan.marks.size)
        assertEquals(text.lastIndex, scan.pairsByOffset.getValue(0).pairedOffset)
    }
}
