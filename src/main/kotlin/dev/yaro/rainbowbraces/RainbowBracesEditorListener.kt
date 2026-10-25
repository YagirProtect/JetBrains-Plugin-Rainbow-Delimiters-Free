package dev.yaro.rainbowbraces

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import dev.yaro.rainbowbraces.editor.RainbowEditorSession
import dev.yaro.rainbowbraces.editor.EditorFileSupport

/**
 * Platform entry point for editor lifetime. Sessions belong to editors rather than
 * documents, since split views of the same file have independent viewports and carets.
 * User data makes attachment idempotent across editor creation and settings refreshes.
 */
class RainbowBracesEditorListener : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
        attach(event.editor)
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        val editor = event.editor
        val session = editor.getUserData(SESSION_KEY) ?: return
        editor.putUserData(SESSION_KEY, null)
        Disposer.dispose(session)
    }

    companion object {
        private val SESSION_KEY = Key.create<RainbowEditorSession>("dev.yaro.rainbowbraces.session")

        fun refreshEditor(editor: Editor) {
            // Applying settings can enable an already-open editor that had no session.
            attach(editor)?.refresh()
        }

        private fun attach(editor: Editor): RainbowEditorSession? {
            // Keep an existing session even when settings were disabled: its refresh
            // removes owned markup and it can be reused after enabling the plugin again.
            editor.getUserData(SESSION_KEY)?.let { return it }
            if (!EditorFileSupport.isEnabled(editor)) return null
            return RainbowEditorSession(editor).also { editor.putUserData(SESSION_KEY, it) }
        }
    }
}
