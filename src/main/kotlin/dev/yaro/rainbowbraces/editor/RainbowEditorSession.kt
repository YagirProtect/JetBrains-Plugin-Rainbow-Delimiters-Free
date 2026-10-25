// Copyright (c) 2026 YagirProtect.
// Licensed under GPL-3.0-only with the JetBrains IDE Linking Exception.
// See LICENSE and LICENSE-EXCEPTION in the project root.

package dev.yaro.rainbowbraces.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm

/**
 * Connects one editor's events to highlighting and owns its disposable resources.
 * A document can appear in several editors, each with its own viewport and caret.
 * Structural updates are debounced on the Swing thread; caret and selection changes
 * use cheaper emphasis/repaint paths so moving the caret does not rebuild all guides.
 */
internal class RainbowEditorSession(private val editor: Editor) : Disposable {
    private val highlighters = RainbowHighlighterManager(editor)
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private var disposed = false

    private val visibleAreaListener = VisibleAreaListener { scheduleUpdate() }
    private val selectionListener = object : SelectionListener {
        override fun selectionChanged(event: SelectionEvent) {
            editor.contentComponent.repaint()
        }
    }
    private val caretListener = object : CaretListener {
        override fun caretPositionChanged(event: CaretEvent) {
            highlighters.refreshPairEmphasis()
            editor.contentComponent.repaint()
        }
    }

    init {
        Disposer.register(this, highlighters)
        editor.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) = scheduleUpdate()
        }, this)
        editor.scrollingModel.addVisibleAreaListener(visibleAreaListener)
        editor.caretModel.addCaretListener(caretListener)
        editor.selectionModel.addSelectionListener(selectionListener)
        RainbowNativeGuideController(editor, this, highlighters::replacesNativeGuide)
        scheduleUpdate()
    }

    fun refresh() {
        // Applying settings must take effect immediately, even if the document stamp
        // has not changed. Discard a pending edit/scroll update to avoid duplicate work.
        if (disposed || editor.isDisposed) return
        alarm.cancelAllRequests()
        highlighters.invalidate()
        highlighters.refresh()
    }

    private fun scheduleUpdate() {
        if (disposed || editor.isDisposed) return
        alarm.cancelAllRequests()
        alarm.addRequest({
            // Disposal may happen between scheduling and execution of this callback.
            if (!disposed && !editor.isDisposed) highlighters.refresh()
        }, UPDATE_DELAY_MS)
    }

    override fun dispose() {
        // The document listener, alarm and highlighter manager belong to the Disposer
        // tree. These three editor listeners require explicit removal instead.
        disposed = true
        editor.scrollingModel.removeVisibleAreaListener(visibleAreaListener)
        editor.caretModel.removeCaretListener(caretListener)
        editor.selectionModel.removeSelectionListener(selectionListener)
    }

    companion object {
        private const val UPDATE_DELAY_MS = 150
    }
}
