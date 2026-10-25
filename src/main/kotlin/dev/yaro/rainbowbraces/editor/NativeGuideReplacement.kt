package dev.yaro.rainbowbraces.editor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.RangeHighlighter

/**
 * Identifies native guides covered by a plugin renderer, allowing the controller to
 * suppress duplicates without disabling the IDE's indent-guide preference globally.
 * The editor is explicit because document markup can be shared by several editors.
 * Return false when coverage is uncertain so unrelated native guides remain visible.
 */
internal interface NativeGuideReplacement {
    fun replacesNativeGuide(editor: Editor, replacement: RangeHighlighter, nativeGuide: RangeHighlighter): Boolean
}
