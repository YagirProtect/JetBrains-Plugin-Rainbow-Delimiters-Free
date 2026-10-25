package dev.yaro.rainbowbraces.editor

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.VisualPosition
import com.intellij.ui.paint.PaintUtil
import java.awt.BasicStroke
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.geom.Rectangle2D

/**
 * Shared drawing rules for colored bracket guides and ordinary switch-section guides.
 *
 * Document lines, visual rows, and device pixels are different coordinate systems: folds and
 * soft wraps change row mapping, block inlays add height between rows, and HiDPI scales user
 * coordinates to device pixels. Always ask the editor for positions instead of multiplying
 * a document line number by lineHeight. Callers choose the guide color and restore graphics state.
 */
internal object IndentGuidePainting {
    fun firstContentOffset(document: Document, line: Int): Int {
        var offset = document.getLineStartOffset(line)
        val end = document.getLineEndOffset(line)
        val text = document.charsSequence
        while (offset < end && (text[offset] == ' ' || text[offset] == '\t')) offset++
        return offset
    }

    /**
     * Draw below [headerLine] through the row preceding [endLine], using visual row indices.
     * [includeEndLine] additionally includes an inline closing-bracket row containing body code.
     * A closing bracket or the next case label on its own row must be excluded. The supplied
     * x-coordinate is already centered by the caller; this helper only clips and avoids text.
     */
    fun paint(
        editor: Editor, g: Graphics2D, x: Int, headerLine: Int, endLine: Int, includeEndLine: Boolean
    ) {
        val document = editor.document
        val clip = g.clipBounds ?: editor.scrollingModel.visibleArea
        // A clip inside a block inlay can map to the next text row. Include the
        // preceding row so its trailing inlay space is still painted.
        val firstLine = maxOf(headerLine, editor.xyToVisualPosition(Point(x, clip.y)).line - 1)
        val lastScopeLine = endLine - if (includeEndLine) 0 else 1
        val lastLine = minOf(lastScopeLine, editor.xyToVisualPosition(Point(x, clip.y + clip.height)).line)
        if (firstLine > lastLine || x < clip.x || x >= clip.x + clip.width) return

        for (visualLine in firstLine..lastLine) {
            val y = editor.visualPositionToXY(VisualPosition(visualLine, 0)).y
            val textBottom = y + editor.lineHeight
            if (visualLine < endLine) {
                // Block inlays add pixels between text rows without adding visual lines.
                val nextY = editor.visualPositionToXY(VisualPosition(visualLine + 1, 0)).y
                paintSegment(g, x, textBottom, nextY - 1, clip)
            }
            if (visualLine == headerLine) continue
            val rowStart = editor.visualPositionToOffset(VisualPosition(visualLine, 0))
            val logicalLine = document.getLineNumber(rowStart)
            val contentOffset = firstContentOffset(document, logicalLine)
            val lineEnd = document.getLineEndOffset(logicalLine)
            val softWrap = editor.softWrapModel.getSoftWrap(rowStart)
            // A continuation row can have less indentation than its original document line.
            // Its virtual indentation, rather than the logical line's whitespace, decides overlap.
            if (softWrap != null && rowStart > document.getLineStartOffset(logicalLine) &&
                softWrap.indentInPixels <= x) continue
            val firstText = maxOf(rowStart, contentOffset)
            val firstTextPosition = editor.offsetToVisualPosition(firstText)
            // Leave gaps where code, a fold placeholder or wrapped text crosses the guide.
            if (firstText < lineEnd && firstTextPosition.line == visualLine &&
                editor.offsetToXY(firstText).x <= x) continue
            paintSegment(g, x, y, textBottom - 1, clip)
        }
    }

    private fun paintSegment(g: Graphics2D, x: Int, startY: Int, endY: Int, clip: Rectangle) {
        val top = maxOf(startY, clip.y)
        val bottom = minOf(endY, clip.y + clip.height - 1)
        if (top > bottom) return
        // Treat the bottom as an exclusive boundary, then align BOTH ends to the device grid.
        // Rounding only the start of each row leaves one-pixel gaps at scales such as 125/150%.
        // Filling the aligned rectangle also handles tiny inlay gaps without reversed line caps.
        val alignedTop = PaintUtil.alignToInt(top.toDouble(), g)
        val alignedEnd = PaintUtil.alignToInt((bottom + 1).toDouble(), g)
        val alignedX = PaintUtil.alignToInt(x.toDouble(), g)
        // Match the platform's inside stroke, including its HiDPI width rounding.
        val strokeWidth = (g.stroke as? BasicStroke)?.lineWidth?.toDouble() ?: 1.0
        val width = PaintUtil.alignToInt(strokeWidth, g)
        g.fill(Rectangle2D.Double(alignedX, alignedTop, width, alignedEnd - alignedTop))
    }
}
