package dev.yaro.rainbowbraces.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.editor.markup.CustomHighlighterOrder
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Disposer
import java.awt.Graphics
import java.util.IdentityHashMap

/**
 * Temporarily decorates native guide renderers instead of changing the IDE's global guide setting.
 *
 * IDEA commonly uses editor markup, while Rider can deliver guides through shared document markup.
 * Observe both existing and newly added highlighters: daemon updates can arrive after our editor
 * session starts, or replace the renderer on an existing range. Ownership is registered under
 * the editor session so listeners and wrapper claims are released when that editor closes.
 *
 * Only suppress a native guide when the manager has a matching replacement at paint time. This
 * preserves unrelated guides and naturally restores native painting when plugin guides are disabled.
 */
internal class RainbowNativeGuideController(
    private val editor: Editor,
    parentDisposable: Disposable,
    private val isReplaced: (RangeHighlighter) -> Boolean
) : Disposable {
    private val wrapped = mutableMapOf<RangeHighlighter, ReplacementAwareRenderer>()
    private var disposed = false

    init {
        Disposer.register(parentDisposable, this)
        // Rider also receives native guides in the shared document markup model.
        val models = listOf(
            editor.markupModel,
            DocumentMarkupModel.forDocument(editor.document, editor.project, true)
        ).distinct()
        models.filterIsInstance<MarkupModelEx>().forEach { markup ->
            markup.addMarkupModelListener(this, object : MarkupModelListener {
                override fun afterAdded(highlighter: RangeHighlighterEx) = wrap(highlighter)

                override fun attributesChanged(highlighter: RangeHighlighterEx, renderersChanged: Boolean, fontStyleOrColorChanged: Boolean) =
                    wrap(highlighter)

                override fun afterRemoved(highlighter: RangeHighlighterEx) {
                    wrapped.remove(highlighter)?.detach(editor)
                }
            })
            markup.allHighlighters.forEach(::wrap)
        }
    }

    private fun wrap(highlighter: RangeHighlighter) {
        if (disposed) return
        val renderer = highlighter.customRenderer
        val replacement = when {
            renderer is ReplacementAwareRenderer -> renderer
            renderer != null && isNativeGuide(renderer) -> ReplacementAwareRenderer(renderer)
            else -> {
                wrapped.remove(highlighter)?.detach(editor)
                return
            }
        }
        // Several editor listeners can observe the same document highlighter. Reuse its wrapper
        // rather than stacking decorators; each editor contributes its own replacement predicate.
        if (wrapped[highlighter] !== replacement) {
            wrapped.put(highlighter, replacement)?.detach(editor)
            replacement.attach(editor, isReplaced)
        }
        // Setting a renderer emits another markup event. Identity checks make this path reentrant.
        if (renderer !== replacement) highlighter.customRenderer = replacement
    }

    private fun isNativeGuide(renderer: CustomHighlighterRenderer): Boolean {
        // The platform moved this renderer to the indentGuide package in 2026.2.
        // Check the hierarchy to include product-specific subclasses without linking to a particular platform version.
        return generateSequence<Class<*>>(renderer.javaClass) { it.superclass }.any {
            it.name == "com.intellij.codeInsight.daemon.impl.IndentGuideRenderer" ||
                it.name == "com.intellij.codeInsight.daemon.impl.indentGuide.IndentGuideRenderer"
        }
    }

    override fun dispose() {
        disposed = true
        wrapped.forEach { (highlighter, renderer) ->
            renderer.detach(editor)
            // Another split editor may still need the wrapper. Also avoid overwriting a newer
            // renderer installed by the daemon since this controller last observed the range.
            if (!renderer.hasEditors() && highlighter.isValid && highlighter.customRenderer === renderer) {
                highlighter.customRenderer = renderer.original
            }
        }
        wrapped.clear()
    }

    // Document highlighters are shared by split editors. Each editor must decide
    // independently whether it has a replacement, and release only its own claim.
    private class ReplacementAwareRenderer(val original: CustomHighlighterRenderer) : CustomHighlighterRenderer {
        private val replacements = IdentityHashMap<Editor, (RangeHighlighter) -> Boolean>()

        fun attach(editor: Editor, isReplaced: (RangeHighlighter) -> Boolean) {
            replacements[editor] = isReplaced
        }

        fun detach(editor: Editor) {
            replacements.remove(editor)
        }

        fun hasEditors(): Boolean = replacements.isNotEmpty()

        override fun getOrder(): CustomHighlighterOrder = original.order

        override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
            if (replacements[editor]?.invoke(highlighter) == true) return
            // A third-party/platform renderer may change stroke, clip, or color. Isolate those
            // changes so decorating it does not alter subsequent custom highlighter painting.
            val copy = g.create()
            try {
                original.paint(editor, highlighter, copy)
            } finally {
                copy.dispose()
            }
        }
    }
}
