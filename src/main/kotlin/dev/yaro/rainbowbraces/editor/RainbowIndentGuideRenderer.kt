// Copyright (c) 2026 YagirProtect.
// Licensed under GPL-3.0-only with the JetBrains IDE Linking Exception.
// See LICENSE and LICENSE-EXCEPTION in the project root.

package dev.yaro.rainbowbraces.editor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.CustomHighlighterOrder
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import dev.yaro.rainbowbraces.editor.IndentGuidePainting.firstContentOffset
import dev.yaro.rainbowbraces.settings.RainbowSettingsService
import dev.yaro.rainbowbraces.scanning.DelimiterScanner
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import kotlin.math.roundToInt

/**
 * Paints one multiline delimiter pair using the same palette entry as its endpoints.
 * The highlighter's moving range supplies current offsets; cached scan offsets must
 * not be used during painting after an edit. Editor coordinate conversions account
 * for tabs, zoom, folds and soft wraps instead of estimating positions from columns.
 */
internal class RainbowIndentGuideRenderer(private val color: Color) : CustomHighlighterRenderer, NativeGuideReplacement {
    // Paint after native indent guides, including the guide selected by the caret.
    override fun getOrder(): CustomHighlighterOrder = CustomHighlighterOrder.AFTER_TEXT

    override fun replacesNativeGuide(
        editor: Editor,
        replacement: RangeHighlighter,
        nativeGuide: RangeHighlighter
    ): Boolean {
        if (!replacement.isValid || !nativeGuide.isValid) return false
        val document = editor.document
        val text = document.charsSequence
        val openOffset = replacement.startOffset
        val closeOffset = replacement.endOffset - 1
        if (openOffset !in text.indices || closeOffset !in text.indices ||
            !DelimiterScanner.matches(text[openOffset], text[closeOffset])
        ) return false
        val openLine = document.getLineNumber(openOffset)
        val closeLine = document.getLineNumber(closeOffset)
        val nativeStartLine = document.getLineNumber(nativeGuide.startOffset)
        val nativeEndLine = document.getLineNumber(nativeGuide.endOffset)
        // Inline closing braces do not outdent the line, so the native guide can
        // continue beyond the brace. Compare containing ranges and indentation.
        if (openLine < nativeStartLine || closeLine > nativeEndLine) return false
        val openIndent = editor.offsetToLogicalPosition(firstContentOffset(document, openLine)).column
        val closeIndentOffset = firstContentOffset(document, closeLine)
        val scopeIndent = if (closeIndentOffset == closeOffset) {
            minOf(openIndent, editor.offsetToLogicalPosition(closeIndentOffset).column)
        } else openIndent
        return scopeIndent == editor.offsetToLogicalPosition(firstContentOffset(document, nativeStartLine)).column
    }

    override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
        if (!highlighter.isValid) return
        val document = editor.document
        val openOffset = highlighter.startOffset
        val closeOffset = highlighter.endOffset - 1
        val text = document.charsSequence
        // Range markers may have moved while a debounced document update is pending.
        if (openOffset !in text.indices || closeOffset !in text.indices ||
            !DelimiterScanner.matches(text[openOffset], text[closeOffset])
        ) return

        val openLine = document.getLineNumber(openOffset)
        val closeLine = document.getLineNumber(closeOffset)
        if (openLine == closeLine) return
        val folding = editor.foldingModel
        if (folding.isOffsetCollapsed(openOffset)) return
        val headerFold = folding.getCollapsedRegionAtOffset(document.getLineEndOffset(openLine))
        if (headerFold != null && headerFold === folding.getCollapsedRegionAtOffset(closeOffset)) return

        val openIndent = firstContentOffset(document, openLine)
        val closeIndent = firstContentOffset(document, closeLine)
        val openX = editor.offsetToXY(openIndent).x
        // A standalone closing brace supplies the scope's indentation. For an inline
        // closing brace use the opening line, avoiding a line through expressions.
        val indentX = if (closeIndent == closeOffset) minOf(openX, editor.offsetToXY(closeIndent).x) else openX
        val braceOffset = if (closeIndent == closeOffset) closeOffset else openOffset
        val braceWidth = (editor.offsetToXY(braceOffset + 1).x - editor.offsetToXY(braceOffset).x).coerceAtLeast(1)
        val x = indentX + braceWidth / 2
        // Center on the delimiter's glyph cell, rather than the left edge of its column.
        // Vertical limits are visual lines; Code Vision adds height between those lines.
        val openVisualLine = editor.offsetToVisualPosition(openOffset).line
        val closeVisualLine = editor.offsetToVisualPosition(closeOffset).line

        val previousColor = g.color
        try {
            val active = editor.caretModel.allCarets.any { caret ->
                // Selection ends are exclusive. Without a selection, either adjacent
                // caret position activates the pair, including an empty pair such as ().
                if (caret.hasSelection()) {
                    openOffset in caret.selectionStart until caret.selectionEnd ||
                            closeOffset in caret.selectionStart until caret.selectionEnd
                } else {
                    caret.offset == openOffset || caret.offset == openOffset + 1 ||
                            caret.offset == closeOffset || caret.offset == closeOffset + 1
                }
            }
            // Blend toward the background rather than black, for both light and dark themes.
            val transparency = RainbowSettingsService.getInstance().indentGuideTransparency(active) / 100.0
            if (transparency == 1.0) return
            val opacity = 1.0 - transparency
            val background = editor.colorsScheme.defaultBackground
            // Mix 20% neutral gray into inactive colors while preserving perceived brightness.
            val gray = color.red * 0.2126 + color.green * 0.7152 + color.blue * 0.0722
            fun blend(channel: Int, backgroundChannel: Int): Int {
                val foreground = if (active) channel.toDouble() else channel * 0.8 + gray * 0.2
                return (foreground * opacity + backgroundChannel * transparency).roundToInt()
            }
            g.color = Color(
                blend(color.red, background.red),
                blend(color.green, background.green),
                blend(color.blue, background.blue)
            )
            IndentGuidePainting.paint(
                editor,
                g as Graphics2D,
                x,
                openVisualLine,
                closeVisualLine,
                closeIndent != closeOffset
            )
        } finally {
            g.color = previousColor
        }
    }
}
