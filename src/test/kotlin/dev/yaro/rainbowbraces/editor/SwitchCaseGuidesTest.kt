package dev.yaro.rainbowbraces.editor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorColors
import dev.yaro.rainbowbraces.settings.RainbowSettingsService
import java.awt.Rectangle
import kotlin.math.roundToInt

class SwitchCaseGuidesTest : RainbowEditorTestCase() {
    private fun caseGuides() = myFixture.editor.markupModel.allHighlighters.filter {
        it.customRenderer is SwitchCaseGuideRenderer
    }.sortedBy { it.startOffset }

    private val sample = """
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

    fun testWhenAndDefaultGuidesUseThemeColorAndStopBeforeTheNextLabel() {
        configure(sample, "guides.cs")
        val editor = myFixture.editor
        val sections = caseGuides()
        assertEquals(3, sections.size)
        val color = editor.colorsScheme.getColor(EditorColors.INDENT_GUIDE_COLOR)!!
        for (section in sections) {
            val labelLine = editor.document.getLineNumber(section.startOffset)
            val top = editor.offsetToXY(editor.document.getLineStartOffset(labelLine + 1)).y
            val bottom = editor.offsetToXY(section.endOffset).y
            val x = guideX(section.startOffset)
            val image = render(section)
            for (y in top until bottom) assertEquals(color.rgb, image.getRGB(x, y))
            assertEquals(0, image.getRGB(x, top - 1))
            assertEquals(0, image.getRGB(x, bottom))
        }
    }

    fun testGuidesAreCreatedForCAndCppButNotOtherLanguages() {
        val text = "switch (x) {\n    case 1:\n        run();\n        break;\n    default:\n        stop();\n}"
        for (extension in listOf("c", "h", "cpp", "hpp", "cs")) {
            configure(text, "switch.$extension")
            assertEquals("Case guides in $extension", 2, caseGuides().size)
        }
        configure(text, "switch.rs")
        assertEmpty(caseGuides())
    }

    fun testStackedLabelsShareOneGuideAndExplicitBlocksKeepTheirBraceGuide() {
        configure("switch (x) {\n    case 0:\n    case 1:\n        run();\n        break;\n    case 2: {\n        stop();\n        break;\n    }\n}", "guides.cpp")
        assertEquals(1, caseGuides().size)
        assertEquals(myFixture.editor.document.text.indexOf("case 1"), caseGuides().single().startOffset)
        assertEquals(2, guides().size)
    }

    fun testNativeCaseGuideIsNotDuplicatedAndReturnsWhenTheFeatureIsDisabled() {
        configure(sample, "guides.cs")
        val native = addNativeGuide(1, 4)
        assertTrue(isBlank(render(native)))
        assertFalse(isBlank(render(caseGuides().first())))
        val settings = RainbowSettingsService.getInstance()
        settings.updatePreferences(settings.preferences().copy(indentGuidesEnabled = false))
        refresh()
        assertEmpty(caseGuides())
        assertFalse(isBlank(render(native)))
    }

    fun testCaseGuidesAreContinuousThroughCodeVisionAtFractionalScales() {
        configure(sample, "guides.cs")
        val editor = myFixture.editor
        val inlay = editor.inlayModel.addBlockElement(editor.document.getLineStartOffset(2), false, true, 0,
            object : EditorCustomElementRenderer {
                override fun calcWidthInPixels(inlay: Inlay<*>): Int = 200
                override fun calcHeightInPixels(inlay: Inlay<*>): Int = editor.lineHeight + 7
            }
        )!!
        val guide = caseGuides().first()
        val top = editor.offsetToXY(guide.startOffset).y + editor.lineHeight
        val bottom = editor.offsetToXY(guide.endOffset).y
        val x = guideX(guide.startOffset)
        for (scale in listOf(1.0, 1.25, 1.5, 1.75, 2.0)) {
            val image = render(guide, scale = scale)
            for (y in (top * scale).roundToInt() until (bottom * scale).roundToInt()) {
                assertTrue("Case guide interrupted at scale=$scale, y=$y", image.getRGB((x * scale).toInt(), y) != 0)
            }
        }
        val hintY = inlay.bounds!!.y + 2
        val clipped = render(guide, Rectangle(x, hintY, 1, 3))
        for (y in hintY until hintY + 3) assertTrue(clipped.getRGB(x, y) != 0)
        assertEquals(0, clipped.getRGB(x, hintY - 1))
        assertEquals(0, clipped.getRGB(x, hintY + 3))
    }

    fun testFoldedSwitchDoesNotDrawCaseGuides() {
        configure(sample, "guides.cs")
        val editor = myFixture.editor
        editor.foldingModel.runBatchFoldingOperation {
            editor.foldingModel.addFoldRegion(sample.indexOf('{'), sample.length, "{...}")!!.isExpanded = false
        }
        assertTrue(caseGuides().isNotEmpty())
        caseGuides().forEach { assertTrue(isBlank(render(it))) }
    }

    fun testRemovingCaseLabelsClearsOldGuides() {
        configure(sample, "guides.cs")
        val previous = caseGuides()
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.setText("void run() { work(); }")
        }
        refresh()
        assertEmpty(caseGuides())
        assertTrue(previous.all { !it.isValid })
    }

    fun testRenamingFileInvalidatesCachedCaseGuideLanguage() {
        configure(sample, "guides.cs")
        val editor = myFixture.editor
        val cache = DelimiterScanCache(editor, dev.yaro.rainbowbraces.scanning.DelimiterScanner())
        assertEquals(3, cache.get(8).caseSections.size)
        val stamp = editor.document.modificationStamp
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.file.virtualFile.rename(this, "guides.rs")
        }
        assertEquals(stamp, editor.document.modificationStamp)
        assertEmpty(cache.get(8).caseSections)
    }
}
