// Copyright (c) 2026 YagirProtect.
// Licensed under GPL-3.0-only with the JetBrains IDE Linking Exception.
// See LICENSE and LICENSE-EXCEPTION in the project root.

package dev.yaro.rainbowbraces.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.*
import com.intellij.openapi.progress.ProcessCanceledException
import dev.yaro.rainbowbraces.scanning.DelimiterScanner
import dev.yaro.rainbowbraces.settings.RainbowSettingsService
import java.awt.Font
import java.awt.Point
import kotlin.math.max
import kotlin.math.min

/**
 * Converts cached document structure into editor-specific, viewport-limited markup.
 * Scanning must cover the whole file to preserve nesting colors, but creating IDE
 * highlighters for the whole file would make scrolling and editing unnecessarily costly.
 * Only this manager's highlighters are removed; other plugins and IDE markup are owned
 * by their respective producers. Pair emphasis has a separate, cheaper update path.
 */
internal class RainbowHighlighterManager(private val editor: Editor) : Disposable {
    private val settings = RainbowSettingsService.getInstance()
    private val scanner = DelimiterScanner()
    private val scanCache = DelimiterScanCache(editor, scanner)
    private val highlighters = mutableListOf<RangeHighlighter>()
    private val emphasisHighlighters = mutableListOf<RangeHighlighter>()

    companion object {
        private val LOG = Logger.getInstance(RainbowHighlighterManager::class.java)
        // Bound markup creation even when a very dense file fits inside the viewport.
        private const val MAX_VISIBLE_HIGHLIGHTERS = 2_000
        private const val MAX_VISIBLE_GUIDES = 500
        private const val MARGIN = 3000
    }

    fun invalidate() = scanCache.invalidate()

    fun replacesNativeGuide(nativeGuide: RangeHighlighter): Boolean = highlighters.any { replacement ->
        (replacement.customRenderer as? NativeGuideReplacement)
            ?.replacesNativeGuide(editor, replacement, nativeGuide) == true
    }

    override fun dispose() = clearHighlighters()

    fun refresh() {
        try {
            if (!EditorFileSupport.isEnabled(editor)) {
                clearHighlighters()
                return
            }

            val doc = editor.document
            val (rangeStart, rangeEnd) = visibleOffsets(editor, doc.textLength)

            val palette = settings.activeColors()
            val delimiters = scanCache.get(palette.size)
            val marks = delimiters.marks
                .asSequence()
                .filter { it.offset in rangeStart..rangeEnd }
                .take(MAX_VISIBLE_HIGHLIGHTERS)
                .toList()

            clearHighlighters()

            for (m in marks) {
                val attrs = TextAttributes(
                    palette[m.colorIndex % palette.size],
                    null, null, null,
                    Font.PLAIN
                )
                val rh = editor.markupModel.addRangeHighlighter(
                    m.offset,
                    m.offset + 1,
                    HighlighterLayer.ADDITIONAL_SYNTAX,
                    attrs,
                    HighlighterTargetArea.EXACT_RANGE
                )
                highlighters.add(rh)
            }
            if (settings.isIndentGuidesEnabled()) {
                delimiters.pairsByOffset.values.asSequence()
                    // The lookup stores both directions; create one guide per opening endpoint.
                    .filter { it.offset < it.pairedOffset }
                    // Keep guides whose braces are both outside the viewport, too.
                    .filter { it.offset <= rangeEnd && it.pairedOffset >= rangeStart }
                    .filter { doc.getLineNumber(it.offset) < doc.getLineNumber(it.pairedOffset) }
                    .take(MAX_VISIBLE_GUIDES)
                    .forEach { pair ->
                        val highlighter = editor.markupModel.addRangeHighlighter(
                            pair.offset,
                            pair.pairedOffset + 1,
                            HighlighterLayer.ADDITIONAL_SYNTAX,
                            null,
                            HighlighterTargetArea.EXACT_RANGE
                        )
                        highlighter.customRenderer = RainbowIndentGuideRenderer(palette[pair.colorIndex % palette.size])
                        highlighters.add(highlighter)
                    }
                val remainingGuides = MAX_VISIBLE_GUIDES - highlighters.count { it.customRenderer is NativeGuideReplacement }
                // Case sections share the guide budget with bracket scopes.
                delimiters.caseSections.asSequence()
                    .filter { it.labelOffset <= rangeEnd && it.endOffset >= rangeStart }
                    .filter { doc.getLineNumber(it.bodyOffset) < doc.getLineNumber(it.endOffset) }
                    .take(remainingGuides)
                    .forEach { section ->
                        val highlighter = editor.markupModel.addRangeHighlighter(
                            section.labelOffset, section.endOffset, HighlighterLayer.ADDITIONAL_SYNTAX,
                            null, HighlighterTargetArea.EXACT_RANGE
                        )
                        highlighter.customRenderer = SwitchCaseGuideRenderer(section.bodyOffset - section.labelOffset)
                        highlighters.add(highlighter)
                    }
            }
            refreshPairEmphasis()
        } catch (e: ProcessCanceledException) {
            // Cancellation is IDE control flow, not a rendering error to log or swallow.
            throw e
        } catch (t: Exception) {
            LOG.warn("Rainbow delimiters update failed", t)
            clearHighlighters()
        }
    }

    private fun clearHighlighters() {
        highlighters.forEach { editor.markupModel.removeHighlighter(it) }
        highlighters.clear()
        clearPairEmphasis()
    }

    fun refreshPairEmphasis() {
        // Use the primary caret for the emphasized pair. Guide renderers independently
        // consider all carets/selections when choosing active versus inactive opacity.
        try {
            if (!EditorFileSupport.isEnabled(editor)) {
                clearPairEmphasis()
                return
            }
            if (!settings.isPairEmphasisEnabled()) {
                clearPairEmphasis()
                return
            }

            val palette = settings.activeColors()
            val delimiters = scanCache.get(palette.size)
            val offset = matchingCandidateOffset()
            val pair = offset?.let { delimiters.pairsByOffset[it] }
            clearPairEmphasis()
            if (pair == null) return

            val color = palette[pair.colorIndex % palette.size]
            emphasisHighlighters.add(addEmphasisHighlighter( pair.offset, color))
            emphasisHighlighters.add(addEmphasisHighlighter( pair.pairedOffset, color))
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (t: Exception) {
            LOG.warn("Rainbow delimiters pair emphasis failed", t)
            clearPairEmphasis()
        }
    }

    private fun matchingCandidateOffset(): Int? {
        // Prefer the delimiter at the caret, then the one immediately before it.
        // This makes either side of a delimiter useful without searching nearby text.
        val doc = editor.document
        val caretOffset = editor.caretModel.offset
        if (caretOffset < doc.textLength && scanner.isDelimiter(doc.charsSequence[caretOffset])) return caretOffset
        val previous = caretOffset - 1
        if (previous >= 0 && scanner.isDelimiter(doc.charsSequence[previous])) return previous
        return null
    }

    private fun addEmphasisHighlighter(offset: Int, color: java.awt.Color): RangeHighlighter {
        // An underline keeps the thin caret visible between adjacent delimiters.
        // Leave the background unset and stay below the real selection layer.
        val attrs = TextAttributes(
            color,
            null,
            color,
            EffectType.LINE_UNDERSCORE,
            Font.PLAIN
        )
        return editor.markupModel.addRangeHighlighter(
            offset,
            offset + 1,
            HighlighterLayer.SELECTION - 1,
            attrs,
            HighlighterTargetArea.EXACT_RANGE
        )
    }

    private fun clearPairEmphasis() {
        emphasisHighlighters.forEach { editor.markupModel.removeHighlighter(it) }
        emphasisHighlighters.clear()
    }

    private fun visibleOffsets(editor: Editor, docLen: Int): Pair<Int, Int> {
        // The character margin reduces churn during small scrolls. It is not an
        // extra scan range: the structural scan already covers the entire document.
        val area = editor.scrollingModel.visibleArea
        val p1 = Point(area.x, area.y)
        val p2 = Point(area.x + area.width, area.y + area.height)

        val start = editor.logicalPositionToOffset(editor.xyToLogicalPosition(p1))
        val end = editor.logicalPositionToOffset(editor.xyToLogicalPosition(p2))

        val a = max(0, start - MARGIN)
        val b = min(docLen, end + MARGIN)
        return a to b
    }


}
