// Copyright (c) 2026 YagirProtect.
// Licensed under GPL-3.0-only with the JetBrains IDE Linking Exception.
// See LICENSE and LICENSE-EXCEPTION in the project root.

package dev.yaro.rainbowbraces.editor

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.markup.CustomHighlighterOrder
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import dev.yaro.rainbowbraces.editor.IndentGuidePainting.firstContentOffset
import java.awt.Graphics
import java.awt.Graphics2D

/**
 * Supplies the ordinary theme guide missing below switch labels without braces.
 * These sections have no delimiter pair, so their color comes from the IDE scheme
 * rather than an arbitrary rainbow level. [headerLength] is relative to the moving
 * highlighter start and includes the label's terminating colon.
 */
internal class SwitchCaseGuideRenderer(private val headerLength: Int) : CustomHighlighterRenderer, NativeGuideReplacement {
    override fun getOrder(): CustomHighlighterOrder = CustomHighlighterOrder.AFTER_TEXT

    override fun replacesNativeGuide(editor: Editor, replacement: RangeHighlighter, nativeGuide: RangeHighlighter): Boolean {
        if (headerEnd(editor.document, replacement) == null || !nativeGuide.isValid) return false
        val document = editor.document
        val labelLine = document.getLineNumber(replacement.startOffset)
        val nativeStartLine = document.getLineNumber(nativeGuide.startOffset)
        if (nativeStartLine != labelLine ||
            document.getLineNumber(nativeGuide.endOffset) > document.getLineNumber(replacement.endOffset)) return false
        return editor.offsetToLogicalPosition(replacement.startOffset).column ==
            editor.offsetToLogicalPosition(firstContentOffset(document, nativeStartLine)).column
    }

    override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
        val headerEnd = headerEnd(editor.document, highlighter) ?: return
        if (editor.foldingModel.isOffsetCollapsed(highlighter.startOffset)) return
        val start = editor.offsetToXY(highlighter.startOffset)
        val width = (editor.offsetToXY(highlighter.startOffset + 1).x - start.x).coerceAtLeast(1)
        val headerLine = editor.offsetToVisualPosition(headerEnd - 1).line
        // A C# when clause may span several lines: start below its colon, not below
        // the case keyword. The next label/switch closing line is an exclusive end.
        val endLine = editor.offsetToVisualPosition(highlighter.endOffset).line
        val previousColor = g.color
        try {
            g.color = editor.colorsScheme.getColor(EditorColors.INDENT_GUIDE_COLOR) ?: return
            IndentGuidePainting.paint(editor, g as Graphics2D, start.x + width / 2, headerLine, endLine, false)
        } finally {
            g.color = previousColor
        }
    }

    private fun headerEnd(document: Document, highlighter: RangeHighlighter): Int? {
        // Edits can invalidate a label before the debounced rescan runs. Do not paint
        // using the old header length unless the expected keyword and colon survive.
        if (!highlighter.isValid) return null
        val start = highlighter.startOffset
        val end = start + headerLength
        val text = document.charsSequence
        if (end <= start || end > highlighter.endOffset || end > text.length || text[end - 1] != ':') return null
        if (!text.startsWith("case", start) && !text.startsWith("default", start)) return null
        return end
    }
}
