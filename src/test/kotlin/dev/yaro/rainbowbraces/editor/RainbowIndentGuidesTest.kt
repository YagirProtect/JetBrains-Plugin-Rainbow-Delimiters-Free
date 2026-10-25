package dev.yaro.rainbowbraces.editor

import com.intellij.testFramework.EditorTestUtil
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import java.awt.Color
import java.awt.Rectangle
import kotlin.math.roundToInt
import kotlin.math.ceil

class RainbowIndentGuidesTest : RainbowEditorTestCase() {
    fun testNestedGuidesUseTheirBraceColors() {
        configure("    outer {\n        inner {\n            body();\n        }\n    }")
        val guides = guides().sortedBy { it.startOffset }
        assertEquals(2, guides.size)
        for (guide in guides) {
            val brace = myFixture.editor.markupModel.allHighlighters.single {
                it.startOffset == guide.startOffset && it.endOffset == guide.startOffset + 1
            }
            val color = brace.getTextAttributes(myFixture.editor.colorsScheme)!!.foregroundColor!!
            val indentOffset = myFixture.editor.document.getLineStartOffset(
                myFixture.editor.document.getLineNumber(guide.startOffset)
            ) + if (guide === guides.first()) 4 else 8
            val x = guideX(indentOffset)
            val y = myFixture.editor.offsetToXY(myFixture.editor.document.getLineStartOffset(2)).y +
                myFixture.editor.lineHeight / 2
            myFixture.editor.caretModel.moveToOffset(guide.startOffset)
            assertEquals(color.rgb, render(guide).getRGB(x, y))
        }
    }

    fun testInactiveGuidesAreMutedAndOnlyActivePairIsBright() {
        configure("    outer {\n        inner {\n            body();\n        }\n    }")
        val editor = myFixture.editor
        val guides = guides().sortedBy { it.startOffset }
        val y = editor.offsetToXY(editor.document.getLineStartOffset(2)).y + editor.lineHeight / 2
        val background = editor.colorsScheme.defaultBackground
        val colors = guides.map { guide ->
            editor.markupModel.allHighlighters.first {
                it.startOffset == guide.startOffset && it.endOffset == guide.startOffset + 1
            }.getTextAttributes(editor.colorsScheme)!!.foregroundColor!!
        }
        fun muted(color: Color): Int {
            val gray = color.red * 0.2126 + color.green * 0.7152 + color.blue * 0.0722
            return Color(
                (color.red * 0.2 + gray * 0.05 + background.red * 0.75).roundToInt(),
                (color.green * 0.2 + gray * 0.05 + background.green * 0.75).roundToInt(),
                (color.blue * 0.2 + gray * 0.05 + background.blue * 0.75).roundToInt()
            ).rgb
        }
        val outerX = guideX(4)
        val innerX = guideX(editor.document.getLineStartOffset(1) + 8)
        assertEquals(muted(colors[0]), render(guides[0]).getRGB(outerX, y))
        editor.caretModel.moveToOffset(guides[1].startOffset + 1)
        assertEquals(colors[1].rgb, render(guides[1]).getRGB(innerX, y))
        assertEquals(muted(colors[0]), render(guides[0]).getRGB(outerX, y))
        editor.caretModel.moveToOffset(guides[1].endOffset)
        assertEquals(colors[1].rgb, render(guides[1]).getRGB(innerX, y))
        editor.caretModel.moveToOffset(editor.document.getLineStartOffset(2) + 14)
        assertEquals(muted(colors[1]), render(guides[1]).getRGB(innerX, y))
        editor.selectionModel.setSelection(guides[0].startOffset, guides[0].startOffset + 1)
        assertEquals(colors[0].rgb, render(guides[0]).getRGB(outerX, y))
        assertEquals(muted(colors[1]), render(guides[1]).getRGB(innerX, y))
        editor.selectionModel.removeSelection()
        assertEquals(muted(colors[0]), render(guides[0]).getRGB(outerX, y))
    }

    fun testOnlyMatchedMultilineDelimiterPairsHaveGuides() {
        configure("    { inline(); }\n    call(\n        [\n            1, 2\n        ]\n    );\n    // {\n    \"{ignored}\"\n    {\n        body();\n    }\n    {")
        assertEquals(3, guides().size)
    }

    fun testMultilineFunctionParametersUseParenthesisColorAndReplaceNativeGuide() {
        configure("pub fn remap<I: ToF64, O: ToF64>(\n    value: I,\n    in_min: I,\n    in_max: I,\n    out_min: O,\n    out_max: O,\n) -> f64 {\n    value.to_f64()\n}")
        val editor = myFixture.editor
        val open = editor.document.text.indexOf('(')
        val close = editor.document.text.indexOf(')')
        val guide = guides().single { it.startOffset == open }
        val color = editor.markupModel.allHighlighters.single {
            it.startOffset == open && it.endOffset == open + 1
        }.getTextAttributes(editor.colorsScheme)!!.foregroundColor!!
        val native = addNativeGuide(0, 6)
        editor.caretModel.moveToOffset(close)
        assertTrue(isBlank(render(native)))
        val x = guideX(close)
        val top = editor.offsetToXY(editor.document.getLineStartOffset(1)).y
        val bottom = editor.offsetToXY(close).y
        val image = render(guide)
        for (y in top until bottom) assertEquals(color.rgb, image.getRGB(x, y))
        assertEquals(0, image.getRGB(x, bottom))
    }

    fun testNestedParenthesesAndSquareBracketsUseTheirOwnGuideColors() {
        configure("    call(\n        [\n            value\n        ]\n    )")
        val editor = myFixture.editor
        val pairs = guides().sortedBy { it.startOffset }
        assertEquals(2, pairs.size)
        val colors = pairs.map { guide ->
            editor.markupModel.allHighlighters.single {
                it.startOffset == guide.startOffset && it.endOffset == guide.startOffset + 1
            }.getTextAttributes(editor.colorsScheme)!!.foregroundColor!!
        }
        assertFalse(colors[0] == colors[1])
        val y = editor.offsetToXY(editor.document.getLineStartOffset(2)).y + editor.lineHeight / 2
        pairs.forEachIndexed { index, guide ->
            editor.caretModel.moveToOffset(guide.startOffset)
            assertEquals(colors[index].rgb, render(guide).getRGB(guideX(guide.endOffset - 1), y))
        }
    }

    fun testGuideSurvivesWhenClosingBraceIsOutsideVisibleRange() {
        configure("    {\n" + "        body();\n".repeat(600) + "    }")
        assertEquals(1, guides().size)
        val close = myFixture.editor.document.textLength - 1
        assertFalse(myFixture.editor.markupModel.allHighlighters.any {
            it.startOffset == close && it.endOffset == close + 1
        })
    }

    fun testTabsUseEditorIndentCoordinatesAndClipPainting() {
        configure("\t{\n\t\tbody();\n\t}")
        val editor = myFixture.editor
        val x = guideX(1)
        val y = editor.offsetToXY(editor.document.getLineStartOffset(1)).y
        val clip = Rectangle(x, y + 3, 1, 4)
        val rendered = render(guides().single(), clip)
        assertEquals(0, rendered.getRGB(x, y + 2))
        assertTrue(rendered.getRGB(x, y + 3) != 0)
        assertEquals(0, rendered.getRGB(x, y + 7))
    }

    fun testGuideIsContinuousBetweenTextRowsAtFractionalDisplayScales() {
        configure("    {\n        first();\n        second();\n\n        last();\n    }")
        val editor = myFixture.editor
        val top = editor.offsetToXY(editor.document.getLineStartOffset(1)).y
        val bottom = editor.offsetToXY(editor.document.getLineStartOffset(5)).y
        for (scale in listOf(1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0)) {
            val image = render(guides().single(), scale = scale)
            val x = (guideX(4) * scale).toInt()
            for (y in ceil(top * scale).toInt() until ceil(bottom * scale).toInt()) {
                assertTrue("Guide interrupted at scale=$scale, y=$y", image.getRGB(x, y) != 0)
            }
        }
    }

    fun testOnePixelBlockInlayDoesNotExtendGuideOntoOutdentedTextOnHiDpi() {
        configure("    {\n        first();\noutdented();\n        last();\n    }")
        val editor = myFixture.editor
        editor.inlayModel.addBlockElement(editor.document.getLineStartOffset(2), false, false, 0,
            object : EditorCustomElementRenderer {
                override fun calcWidthInPixels(inlay: Inlay<*>): Int = 200
                override fun calcHeightInPixels(inlay: Inlay<*>): Int = 1
            }
        )!!
        val textTop = editor.offsetToXY(editor.document.getLineStartOffset(2)).y
        for (scale in listOf(1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0)) {
            val image = render(guides().single(), scale = scale)
            val x = (guideX(4) * scale).toInt()
            val gapTop = ((textTop + editor.lineHeight) * scale).roundToInt()
            val gapEnd = ((textTop + editor.lineHeight + 1) * scale).roundToInt()
            for (y in (textTop * scale).roundToInt() until gapTop) {
                assertEquals("Guide overlaps text at scale=$scale, y=$y", 0, image.getRGB(x, y))
            }
            for (y in gapTop until gapEnd) {
                assertTrue("Guide interrupted in a small inlay at scale=$scale, y=$y", image.getRGB(x, y) != 0)
            }
        }
    }

    fun testGuideIsContinuousAcrossCodeVisionInlaysAndStopsAtBraces() {
        configure("    trait SafeAdd {\n        // Comment\n        fn safe_add();\n    }")
        val editor = myFixture.editor
        val document = editor.document
        val hint = object : EditorCustomElementRenderer {
            override fun calcWidthInPixels(inlay: Inlay<*>): Int = 200
            override fun calcHeightInPixels(inlay: Inlay<*>): Int = editor.lineHeight + 7
        }
        fun addHint(line: Int, above: Boolean) = editor.inlayModel.addBlockElement(
            document.getLineStartOffset(line), false, above, 0, hint
        )!!
        // Both Code Vision above declarations and block inlays below lines occupy extra height.
        val outsideAbove = addHint(0, true)
        addHint(0, false)
        val implementations = addHint(2, true)
        addHint(2, true)
        addHint(3, true)
        val outsideBelow = addHint(3, false)
        val x = guideX(4)
        val top = editor.offsetToXY(document.getLineStartOffset(0)).y + editor.lineHeight
        val bottom = editor.offsetToXY(document.getLineStartOffset(3)).y
        val guide = guides().single()
        val image = render(guide)
        for (scale in listOf(1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0)) {
            val scaledImage = render(guide, scale = scale)
            val scaledX = (x * scale).toInt()
            for (y in (top * scale).roundToInt() until (bottom * scale).roundToInt()) {
                assertTrue("Guide interrupted between braces at scale=$scale, y=$y", scaledImage.getRGB(scaledX, y) != 0)
            }
            assertEquals(0, scaledImage.getRGB(scaledX, (bottom * scale).roundToInt()))
        }
        assertEquals(0, image.getRGB(x, outsideAbove.bounds!!.y))
        assertEquals(0, image.getRGB(x, bottom))
        assertEquals(0, image.getRGB(x, outsideBelow.bounds!!.y))

        // Scrolling may clip the entire paint request to the middle of an inlay.
        val hintY = implementations.bounds!!.y + 2
        val clipped = render(guide, Rectangle(x, hintY, 1, 3))
        for (y in hintY until hintY + 3) assertEquals(image.getRGB(x, y), clipped.getRGB(x, y))
        assertEquals(0, clipped.getRGB(x, hintY - 1))
        assertEquals(0, clipped.getRGB(x, hintY + 3))
    }

    fun testFoldedBlockDoesNotPaintGuide() {
        configure("    {\n        body();\n    }")
        val editor = myFixture.editor
        editor.foldingModel.runBatchFoldingOperation {
            editor.foldingModel.addFoldRegion(4, editor.document.textLength, "{...}")!!.isExpanded = false
        }
        val rendered = render(guides().single())
        assertFalse((0 until rendered.height).any { y ->
            (0 until rendered.width).any { x -> rendered.getRGB(x, y) != 0 }
        })
    }

    fun testRendererDoesNotDrawOverOutdentedCode() {
        configure("    {\nbody();\n    }")
        val editor = myFixture.editor
        val x = guideX(4)
        val y = editor.offsetToXY(editor.document.getLineStartOffset(1)).y + editor.lineHeight / 2
        assertEquals(0, render(guides().single()).getRGB(x, y))
    }

    fun testRendererLeavesGapsForSoftWrappedText() {
        configure("        {\n            " + "word ".repeat(60) + "\n        }")
        val editor = myFixture.editor
        EditorTestUtil.configureSoftWraps(editor, 25, false)
        val bodyLine = editor.document.getLineStartOffset(1)
        val firstVisual = editor.offsetToVisualPosition(bodyLine).line
        val lastVisual = editor.offsetToVisualPosition(editor.document.getLineEndOffset(1)).line
        assertTrue(lastVisual > firstVisual)
        val x = guideX(8)
        val y = editor.visualPositionToXY(com.intellij.openapi.editor.VisualPosition(firstVisual + 1, 0)).y +
            editor.lineHeight / 2
        val rowStart = editor.visualPositionToOffset(com.intellij.openapi.editor.VisualPosition(firstVisual + 1, 0))
        assertTrue(editor.softWrapModel.getSoftWrap(rowStart)!!.indentInPixels < x)
        assertEquals(0, render(guides().single()).getRGB(x, y))
    }
}
