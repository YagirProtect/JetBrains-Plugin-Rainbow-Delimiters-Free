package dev.yaro.rainbowbraces.editor

import com.intellij.codeInsight.daemon.impl.indentGuide.IndentGuideRenderer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.yaro.rainbowbraces.RainbowBracesEditorListener
import dev.yaro.rainbowbraces.settings.RainbowSettingsService
import dev.yaro.rainbowbraces.settings.RainbowSettingsState
import java.awt.Color
import java.awt.Rectangle
import java.awt.image.BufferedImage

abstract class RainbowEditorTestCase : BasePlatformTestCase() {
    private lateinit var previousSettings: RainbowSettingsState

    override fun setUp() {
        super.setUp()
        val service = RainbowSettingsService.getInstance()
        previousSettings = service.state
        service.loadState(RainbowSettingsState())
    }

    override fun tearDown() {
        try {
            if (::previousSettings.isInitialized) {
                RainbowSettingsService.getInstance().loadState(previousSettings)
            }
        } finally {
            super.tearDown()
        }
    }

    protected fun addNativeGuide(startLine: Int, endLine: Int): RangeHighlighter =
        myFixture.editor.markupModel.addRangeHighlighter(
            myFixture.editor.document.getLineStartOffset(startLine),
            myFixture.editor.document.getLineStartOffset(endLine),
            0, null, HighlighterTargetArea.EXACT_RANGE
        ).also { it.customRenderer = IndentGuideRenderer() }

    protected fun isBlank(image: BufferedImage) = (0 until image.height).all { y ->
        (0 until image.width).all { x -> image.getRGB(x, y) == 0 }
    }

    protected fun configure(text: String, fileName: String = "guides.rs") {
        myFixture.configureByText(fileName, text)
        myFixture.editor.component.setSize(800, 600)
        myFixture.editor.contentComponent.setSize(800, 600)
        refresh()
    }

    protected fun refresh() = RainbowBracesEditorListener.refreshEditor(myFixture.editor)

    protected fun guideX(offset: Int): Int {
        val editor = myFixture.editor
        return (editor.offsetToXY(offset).x + editor.offsetToXY(offset + 1).x) / 2
    }

    protected fun guides() = myFixture.editor.markupModel.allHighlighters.filter {
        it.customRenderer is RainbowIndentGuideRenderer
    }

    protected fun render(
        guide: RangeHighlighter,
        clip: Rectangle = Rectangle(0, 0, 800, 600),
        scale: Double = 1.0,
        checkColor: Boolean = true,
        renderer: com.intellij.openapi.editor.markup.CustomHighlighterRenderer = guide.customRenderer!!,
        editor: com.intellij.openapi.editor.Editor = myFixture.editor
    ): BufferedImage {
        val image = BufferedImage((800 * scale).toInt(), (600 * scale).toInt(), BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.scale(scale, scale)
            graphics.clip = clip
            graphics.color = Color.MAGENTA
            renderer.paint(editor, guide, graphics)
            if (checkColor) assertEquals(Color.MAGENTA, graphics.color)
        } finally {
            graphics.dispose()
        }
        return image
    }
}
