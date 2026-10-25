package dev.yaro.rainbowbraces.editor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorFactory
import dev.yaro.rainbowbraces.RainbowBracesEditorListener
import dev.yaro.rainbowbraces.settings.RainbowSettingsService

class RainbowEditorSessionTest : RainbowEditorTestCase() {
    fun testDelimiterCacheTracksDocumentAndPaletteWithoutRescanningForCaretMovement() {
        configure("    { call([1]); }")
        val editor = myFixture.editor
        val cache = DelimiterScanCache(editor, dev.yaro.rainbowbraces.scanning.DelimiterScanner())
        val initial = cache.get(8)
        editor.caretModel.moveToOffset(7)
        assertSame(initial, cache.get(8))
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            editor.document.setText("    { call([[]]); }")
        }
        val changed = cache.get(8)
        assertNotSame(initial, changed)
        val smallerPalette = cache.get(2)
        assertNotSame(changed, smallerPalette)
        assertTrue(smallerPalette.marks.all { it.colorIndex in 0..1 })
    }

    fun testToggleClearsGuidesAndKeepsBracketColors() {
        configure("    {\n        body();\n    }")
        val service = RainbowSettingsService.getInstance()
        assertTrue(service.preferences().indentGuidesEnabled)
        val oldGuides = guides()
        assertEquals(1, oldGuides.size)
        service.updatePreferences(service.preferences().copy(indentGuidesEnabled = false))
        refresh()
        assertEmpty(guides())
        assertTrue(oldGuides.all { !it.isValid })
        assertTrue(myFixture.editor.markupModel.allHighlighters.any { it.endOffset == it.startOffset + 1 })
        service.updatePreferences(service.preferences().copy(indentGuidesEnabled = true))
        refresh()
        assertEquals(1, guides().size)
        service.updatePreferences(service.preferences().copy(enabled = false))
        refresh()
        assertEmpty(myFixture.editor.markupModel.allHighlighters.toList())
    }

    fun testEnablingPluginAttachesPreviouslyDisabledEditor() {
        RainbowSettingsService.getInstance().updatePreferences(RainbowSettingsService.getInstance().preferences().copy(enabled = false))
        configure("    {\n        body();\n    }")
        assertEmpty(guides())
        RainbowSettingsService.getInstance().updatePreferences(RainbowSettingsService.getInstance().preferences().copy(enabled = true))
        refresh()
        assertEquals(1, guides().size)
    }

    fun testDocumentEditsRemoveObsoleteGuides() {
        configure("    {\n        body();\n    }")
        val oldGuides = guides()
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.setText("    { body(); }")
        }
        refresh()
        assertEmpty(guides())
        assertTrue(oldGuides.all { !it.isValid })
    }

    fun testAdjacentBracketsUseUnderlineWithoutObscuringCaret() {
        configure("() {} [] value")
        val editor = myFixture.editor
        fun emphasis() = editor.markupModel.allHighlighters.filter {
            it.layer == com.intellij.openapi.editor.markup.HighlighterLayer.SELECTION - 1
        }
        for (insidePair in listOf(1, 4, 7)) {
            editor.caretModel.moveToOffset(insidePair)
            val highlighted = emphasis()
            assertEquals(setOf(insidePair - 1, insidePair), highlighted.map { it.startOffset }.toSet())
            highlighted.forEach { highlighter ->
                val attributes = highlighter.getTextAttributes(editor.colorsScheme)!!
                assertEquals(com.intellij.openapi.editor.markup.EffectType.LINE_UNDERSCORE, attributes.effectType)
                assertEquals(java.awt.Font.PLAIN, attributes.fontType)
                assertNull(attributes.backgroundColor)
                assertEquals(highlighter.startOffset + 1, highlighter.endOffset)
            }
        }
        editor.caretModel.moveToOffset(editor.document.textLength)
        assertEmpty(emphasis())
    }

    fun testEditorReleaseDisposesGuides() {
        configure("    {\n        body();\n    }")
        val editor = EditorFactory.getInstance().createEditor(myFixture.editor.document, project)
        RainbowBracesEditorListener.refreshEditor(editor)
        val highlighters = editor.markupModel.allHighlighters.toList()
        assertTrue(highlighters.any { it.customRenderer is RainbowIndentGuideRenderer })
        EditorFactory.getInstance().releaseEditor(editor)
        assertTrue(highlighters.all { !it.isValid })
    }
}
