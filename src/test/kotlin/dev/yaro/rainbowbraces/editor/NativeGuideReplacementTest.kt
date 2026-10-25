// Copyright (c) 2026 YagirProtect.
// Licensed under GPL-3.0-only with the JetBrains IDE Linking Exception.
// See LICENSE and LICENSE-EXCEPTION in the project root.

package dev.yaro.rainbowbraces.editor

import com.intellij.codeInsight.daemon.impl.indentGuide.IndentGuideRenderer
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.util.Disposer
import dev.yaro.rainbowbraces.settings.RainbowSettingsService
import java.awt.image.BufferedImage

class NativeGuideReplacementTest : RainbowEditorTestCase() {
    fun testSharedDocumentGuideUsesEachEditorAndRestoresAfterLastControllerCloses() {
        val settings = RainbowSettingsService.getInstance()
        settings.updatePreferences(settings.preferences().copy(enabled = false))
        configure("    {\n        body();\n    }")
        val first = myFixture.editor
        val second = EditorFactory.getInstance().createEditor(first.document, project)
        second.component.setSize(800, 600)
        second.contentComponent.setSize(800, 600)
        val markup = DocumentMarkupModel.forDocument(first.document, project, true)
        val native = markup.addRangeHighlighter(
            0, first.document.getLineStartOffset(2), 0, null, HighlighterTargetArea.EXACT_RANGE
        )
        val firstLifetime = Disposer.newDisposable()
        val secondLifetime = Disposer.newDisposable()
        try {
            native.customRenderer = IndentGuideRenderer()
            RainbowNativeGuideController(first, firstLifetime) { true }
            assertTrue(isBlank(render(native)))
            assertFalse("An editor without a replacement keeps the native guide", isBlank(render(native, editor = second)))

            var secondHasReplacement = false
            RainbowNativeGuideController(second, secondLifetime) { secondHasReplacement }
            assertTrue(isBlank(render(native)))
            assertFalse(isBlank(render(native, editor = second)))
            secondHasReplacement = true
            assertTrue(isBlank(render(native, editor = second)))

            Disposer.dispose(firstLifetime)
            assertFalse(isBlank(render(native)))
            assertTrue("Closing the first controller must not restore the second editor's native guide", isBlank(render(native, editor = second)))

            val newOriginal = object : IndentGuideRenderer() {}
            native.customRenderer = newOriginal
            assertFalse(isBlank(render(native)))
            assertTrue(isBlank(render(native, editor = second)))
            Disposer.dispose(secondLifetime)
            assertSame(newOriginal, native.customRenderer)
        } finally {
            if (!Disposer.isDisposed(firstLifetime)) Disposer.dispose(firstLifetime)
            if (!Disposer.isDisposed(secondLifetime)) Disposer.dispose(secondLifetime)
            markup.removeHighlighter(native)
            EditorFactory.getInstance().releaseEditor(second)
        }
    }

    fun testDocumentGuidesCreatedBeforeEnablingPluginAreReplaced() {
        val settings = RainbowSettingsService.getInstance()
        settings.updatePreferences(settings.preferences().copy(enabled = false))
        configure("    {\n        body();\n    }")
        val editor = myFixture.editor
        val markup = DocumentMarkupModel.forDocument(editor.document, project, true)
        val native = markup.addRangeHighlighter(
            0, editor.document.getLineStartOffset(2), 0, null, HighlighterTargetArea.EXACT_RANGE
        ).also { it.customRenderer = IndentGuideRenderer() }
        try {
            assertFalse(isBlank(render(native, checkColor = false)))
            settings.updatePreferences(settings.preferences().copy(enabled = true))
            refresh()
            assertTrue(isBlank(render(native)))
        } finally {
            markup.removeHighlighter(native)
        }
    }

    fun testDocumentLevelNativeGuidesAreReplacedAndFollowRendererChanges() {
        configure("    {\n        expression\n            continuation\n        next\n    }")
        val editor = myFixture.editor
        val markup = DocumentMarkupModel.forDocument(editor.document, project, true)
        fun addGuide(startLine: Int, endLine: Int) = markup.addRangeHighlighter(
            editor.document.getLineStartOffset(startLine), editor.document.getLineStartOffset(endLine),
            0, null, HighlighterTargetArea.EXACT_RANGE
        ).also { it.customRenderer = object : IndentGuideRenderer() {} }
        val native = addGuide(0, 4)
        val otherNative = addGuide(1, 3)
        try {
            assertTrue("Document-level native guide must be replaced too", isBlank(render(native)))
            assertFalse(isBlank(render(otherNative)))
            native.customRenderer = object : IndentGuideRenderer() {}
            assertTrue(isBlank(render(native)))
            RainbowSettingsService.getInstance().updatePreferences(
                RainbowSettingsService.getInstance().preferences().copy(indentGuidesEnabled = false)
            )
            refresh()
            assertFalse(isBlank(render(native)))
        } finally {
            markup.removeHighlighter(native)
            markup.removeHighlighter(otherNative)
        }
    }

    fun testNativeGuideIsReplacedWhileOtherNativeGuidesRemainVisible() {
        configure("    {\n        expression\n            continuation\n        next\n    }")
        val native = addNativeGuide(0, 4)
        val otherNative = addNativeGuide(1, 3)
        assertTrue(isBlank(render(native)))
        assertFalse(isBlank(render(otherNative)))
        assertFalse(isBlank(render(guides().single())))
        // The daemon can replace the renderer on an existing highlighter.
        native.customRenderer = IndentGuideRenderer()
        assertTrue(isBlank(render(native)))
        RainbowSettingsService.getInstance().updatePreferences(RainbowSettingsService.getInstance().preferences().copy(indentGuidesEnabled = false))
        refresh()
        assertFalse(isBlank(render(native)))
        assertFalse(isBlank(render(otherNative)))
    }

    fun testInlineClosingBraceReplacesExtendedNativeGuide() {
        configure("    if (str.isEmpty()){\n        str = \"5\";\n        return 1;}\n\n    return 0;\n")
        val editor = myFixture.editor
        editor.settings.isIndentGuidesShown = true
        val pass = com.intellij.testFramework.PlatformTestUtil.waitForFuture(
            com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread(java.util.concurrent.Callable {
                com.intellij.openapi.application.ReadAction.compute<com.intellij.codeInsight.daemon.impl.indentGuide.IndentGuidePass, RuntimeException> {
                    com.intellij.codeInsight.daemon.impl.indentGuide.IndentGuidePass(project, editor, myFixture.file).also {
                        it.doCollectInformation(com.intellij.openapi.progress.EmptyProgressIndicator())
                    }
                }
            })
        )
        pass.doApplyInformationToEditor()
        val native = editor.markupModel.allHighlighters.single {
            it.layer == 0 && it.customRenderer != null && editor.document.getLineNumber(it.startOffset) == 0
        }
        val guide = guides().single()
        assertTrue(editor.document.getLineNumber(native.endOffset) > editor.document.getLineNumber(guide.endOffset - 1))
        assertTrue("The native indentation range extends past the inline closing brace", isBlank(render(native)))
        val closingLineY = editor.offsetToXY(editor.document.getLineStartOffset(2)).y + editor.lineHeight / 2
        assertTrue("The colored guide includes code before the inline closing brace", render(guide).getRGB(guideX(4), closingLineY) != 0)
    }

    fun testContainingNativeGuideAtDifferentIndentRemainsVisible() {
        configure("    expression\n        if (ready) {\n            value; }\n        other\n    end")
        val parentGuide = addNativeGuide(0, 4)
        assertFalse(isBlank(render(parentGuide)))
        assertEquals(1, guides().size)
    }

    fun testNativeGuidesCreatedBeforeEnablingPluginAreReplaced() {
        RainbowSettingsService.getInstance().updatePreferences(RainbowSettingsService.getInstance().preferences().copy(enabled = false))
        configure("    {\n        body();\n    }")
        val native = addNativeGuide(0, 2)
        assertFalse(isBlank(render(native, checkColor = false)))
        RainbowSettingsService.getInstance().updatePreferences(RainbowSettingsService.getInstance().preferences().copy(enabled = true))
        refresh()
        assertTrue(isBlank(render(native)))
        assertFalse(isBlank(render(guides().single())))
    }

    fun testGuideThicknessMatchesNativeOnHiDpi() {
        configure("    {\n        body();\n    }")
        val guide = guides().single()
        val native = addNativeGuide(0, 2)
        val nativeRenderer = IndentGuideRenderer()
        for (scale in listOf(1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0)) {
            val rainbowImage = render(guide, scale = scale)
            val nativeImage = render(native, scale = scale, checkColor = false, renderer = nativeRenderer)
            val y = ((myFixture.editor.offsetToXY(myFixture.editor.document.getLineStartOffset(1)).y +
                myFixture.editor.lineHeight / 2) * scale).toInt()
            fun width(image: BufferedImage) = (0 until image.width).count { image.getRGB(it, y) != 0 }
            assertTrue(width(nativeImage) > 0)
            assertEquals("Stroke width at scale $scale", width(nativeImage), width(rainbowImage))
        }
    }
}
