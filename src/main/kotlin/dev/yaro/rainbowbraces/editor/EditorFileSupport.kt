package dev.yaro.rainbowbraces.editor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import dev.yaro.rainbowbraces.settings.RainbowSettingsService

/** Shared eligibility rule for editor creation and later settings/document changes. */
internal object EditorFileSupport {
    private const val MAX_FILE_CHARS = 500_000

    fun isEnabled(editor: Editor): Boolean {
        // Scanning is synchronous, so avoid very large documents. A virtual file is
        // required to resolve the extension; unbacked temporary editors are skipped.
        // Recheck on refresh because both settings and document length can change.
        if (editor.isDisposed || editor.document.textLength > MAX_FILE_CHARS) return false
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return false
        return RainbowSettingsService.getInstance().isEnabledForExtension(file.extension)
    }
}
