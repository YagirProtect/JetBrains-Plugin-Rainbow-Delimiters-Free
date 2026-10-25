// Copyright (c) 2026 YagirProtect.
// Licensed under GPL-3.0-only with the JetBrains IDE Linking Exception.
// See LICENSE and LICENSE-EXCEPTION in the project root.

package dev.yaro.rainbowbraces.editor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import dev.yaro.rainbowbraces.scanning.DelimiterScan
import dev.yaro.rainbowbraces.scanning.DelimiterScanner

/**
 * Reuses the full-document structure while scrolling or moving the caret.
 * Palette length is part of the key because scanning assigns color indices modulo
 * that length. RGB/brightness changes do not affect structure. The extension is also
 * significant: a rename can change language-specific scanning without editing text.
 */
internal class DelimiterScanCache(private val editor: Editor, private val scanner: DelimiterScanner) {
    private var documentStamp = -1L
    private var paletteSize = -1
    private var fileExtension: String? = null
    private var scan = DelimiterScan()

    fun get(colorCount: Int): DelimiterScan {
        val stamp = editor.document.modificationStamp
        val extension = FileDocumentManager.getInstance().getFile(editor.document)?.extension
        if (stamp != documentStamp || colorCount != paletteSize || extension != fileExtension) {
            scan = scanner.scan(editor, colorCount)
            documentStamp = stamp
            paletteSize = colorCount
            fileExtension = extension
        }
        return scan
    }

    fun invalidate() {
        documentStamp = -1L
    }
}
