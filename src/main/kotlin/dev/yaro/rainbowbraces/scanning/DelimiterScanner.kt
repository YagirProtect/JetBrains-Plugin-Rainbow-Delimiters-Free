// Copyright (c) 2026 YagirProtect.
// Licensed under GPL-3.0-only with the JetBrains IDE Linking Exception.
// See LICENSE and LICENSE-EXCEPTION in the project root.

package dev.yaro.rainbowbraces.scanning

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.tree.TokenSet
import kotlin.math.max
import kotlin.math.min

/** A single bracket to color; [colorIndex] is a palette slot, not a resolved theme color. */
internal data class DelimiterMark(val offset: Int, val colorIndex: Int)
private data class OpenDelimiter(val ch: Char, val offset: Int, val colorIndex: Int)
/** One direction of a matched pair. The scan stores an entry for each endpoint for constant-time lookup. */
internal data class DelimiterPair(val offset: Int, val pairedOffset: Int, val colorIndex: Int)
/**
 * Detached scan result shared by bracket coloring, caret emphasis, and indent guides.
 *
 * Offsets refer to the document revision that was scanned. Opening brackets may have marks
 * without a matching pair while the user is typing. Consumers must not assume every mark
 * appears in [pairsByOffset], and painters must revalidate ranges after document edits.
 * Collections are copied because this result is cached after the scanner's builders are discarded.
 */
internal class DelimiterScan(
    marks: List<DelimiterMark> = emptyList(),
    pairs: Map<Int, DelimiterPair> = emptyMap(),
    caseSections: List<SwitchCaseSection> = emptyList()
) {
    val marks: List<DelimiterMark> = java.util.List.copyOf(marks)
    val pairsByOffset: Map<Int, DelimiterPair> = java.util.Map.copyOf(pairs)
    val caseSections: List<SwitchCaseSection> = java.util.List.copyOf(caseSections)
}

/**
 * Produces structural offsets without creating markup or resolving palette colors.
 *
 * Prefer the IDE's syntax lexer so language plugins decide which tokens are strings or comments.
 * Use the manual state machine when PSI/lexing is unavailable or the document exceeds the lexer
 * budget. The fallback handles common literal forms; it is deliberately not a complete grammar
 * for every supported language, particularly embedded code and string interpolation.
 *
 * Scan from the document beginning even when only the viewport will be painted: nesting depth
 * and a visible closing bracket can depend on an opening bracket far above the viewport.
 * The paletteSize arguments must be positive; settings enforce the stronger palette minimum.
 */
internal class DelimiterScanner {
    companion object {
        private const val MAX_LEXER_CHARS = 250_000

        fun matches(open: Char, close: Char): Boolean = when (open) {
            '{' -> close == '}'
            '(' -> close == ')'
            '[' -> close == ']'
            else -> false
        }
    }

    /** Editor-independent fallback used by structural tests and small in-memory samples. */
    fun scanText(text: CharSequence, paletteSize: Int): DelimiterScan = scanText(text, paletteSize, false)

    fun scanText(text: CharSequence, paletteSize: Int, includeSwitchSections: Boolean): DelimiterScan =
        computeMarksManual(text, text.length, 0, text.length, paletteSize,
            if (includeSwitchSections) SwitchCaseScanner(text) else null)

    /** Read the current document under the caller's platform read access; retain no PSI or editor in the result. */
    fun scan(editor: Editor, paletteSize: Int): DelimiterScan {
        val rangeStart = 0
        val rangeEnd = editor.document.textLength
        val doc = editor.document
        val text = doc.charsSequence
        val scanEnd = min(doc.textLength, rangeEnd)

        val project = editor.project
        val vf = FileDocumentManager.getInstance().getFile(doc)
        // Switch sections are language-specific; do not interpret similar words in Rust or other files.
        // Feed the collector during this same pass so it shares our string/comment classification.
        val cases = if (SwitchCaseScanner.supportsExtension(vf?.extension)) SwitchCaseScanner(text) else null
        if (project == null || vf == null || scanEnd > MAX_LEXER_CHARS) {
            return computeMarksManual(text, scanEnd, rangeStart, rangeEnd, paletteSize, cases)
        }

        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(doc)
            ?: return computeMarksManual(text, scanEnd, rangeStart, rangeEnd, paletteSize, cases)

        val language = psiFile.viewProvider.baseLanguage
        val parserDef = LanguageParserDefinitions.INSTANCE.forLanguage(language)
        val commentTokens: TokenSet = parserDef?.commentTokens ?: TokenSet.EMPTY
        val stringTokens: TokenSet = parserDef?.stringLiteralElements ?: TokenSet.EMPTY

        val highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(language, project, vf)
            ?: return computeMarksManual(text, scanEnd, rangeStart, rangeEnd, paletteSize, cases)

        val lexer = highlighter.highlightingLexer
        lexer.start(text, 0, scanEnd)

        val out = ArrayList<DelimiterMark>(512)
        val pairs = HashMap<Int, DelimiterPair>()
        val stack = ArrayDeque<OpenDelimiter>()

        while (lexer.tokenType != null) {
            val tt = lexer.tokenType!!
            val ts = lexer.tokenStart
            val te = lexer.tokenEnd

            val ignored = commentTokens.contains(tt) || stringTokens.contains(tt)
            if (!ignored) {
                val from = max(0, ts)
                val to = min(scanEnd, te)

                for (i in from until to) {
                    cases?.accept(i)
                    val c = text[i]
                    when (c) {
                        '{', '(', '[' -> {
                            val color = stack.size % paletteSize
                            stack.addLast(OpenDelimiter(c, i, color))
                            if (i in rangeStart..rangeEnd) out.add(DelimiterMark(i, color))
                        }
                        '}', ')', ']' -> {
                            // Do not search deeper in the stack to repair malformed input. Inventing a
                            // match during an incomplete edit would assign unrelated endpoints one color.
                            val open = if (stack.isNotEmpty() && matches(stack.last().ch, c)) stack.removeLast() else null
                            if (open != null) {
                                pairs[open.offset] = DelimiterPair(open.offset, i, open.colorIndex)
                                pairs[i] = DelimiterPair(i, open.offset, open.colorIndex)
                                if (i in rangeStart..rangeEnd) out.add(DelimiterMark(i, open.colorIndex))
                            }
                        }
                    }
                }
            }

            lexer.advance()
        }

        return DelimiterScan(out, pairs, cases?.finish(pairs).orEmpty())
    }

    private enum class Mode {
        CODE, LINE_COMMENT, BLOCK_COMMENT, STRING, CHAR, CS_VERBATIM, RUST_RAW
    }

    /**
     * Single-pass fallback with explicit literal/comment modes. Recognize a literal prefix before
     * forwarding any of its contents to the switch collector. Keep bracket matching behavior in
     * sync with the lexer path when adding support for another literal form.
     */
    private fun computeMarksManual(
        text: CharSequence,
        scanEnd: Int,
        rangeStart: Int,
        rangeEnd: Int,
        paletteSize: Int,
        cases: SwitchCaseScanner?
    ): DelimiterScan {
        val out = ArrayList<DelimiterMark>(512)
        val pairs = HashMap<Int, DelimiterPair>()
        val stack = ArrayDeque<OpenDelimiter>()

        var mode = Mode.CODE

        // Rust raw string: r#" ... "# (hashCount>=0)
        var rustRawHashes = 0

        var i = 0
        while (i < scanEnd) {
            val c = text[i]
            val n = if (i + 1 < scanEnd) text[i + 1] else '\u0000'
            val n2 = if (i + 2 < scanEnd) text[i + 2] else '\u0000'

            when (mode) {
                Mode.CODE -> {
                    // --- comments ---
                    if (c == '/' && n == '/') { mode = Mode.LINE_COMMENT; i += 2; continue }
                    if (c == '/' && n == '*') { mode = Mode.BLOCK_COMMENT; i += 2; continue }

                    // --- C# strings ---
                    // @"..."
                    if (c == '@' && n == '"') { mode = Mode.CS_VERBATIM; i += 2; continue }
                    // $@"..." or @$"..."
                    if (c == '$' && n == '@' && n2 == '"') { mode = Mode.CS_VERBATIM; i += 3; continue }
                    if (c == '@' && n == '$' && n2 == '"') { mode = Mode.CS_VERBATIM; i += 3; continue }

                    // normal "..."
                    if (c == '"') { mode = Mode.STRING; i++; continue }
                    // char 'a'
                    if (c == '\'') { mode = Mode.CHAR; i++; continue }

                    // --- Rust raw strings: r###" ... "### ---
                    if (c == 'r') {
                        var j = i + 1
                        var hashes = 0
                        while (j < scanEnd && text[j] == '#') { hashes++; j++ }
                        if (j < scanEnd && text[j] == '"') {
                            rustRawHashes = hashes
                            mode = Mode.RUST_RAW
                            i = j + 1
                            continue
                        }
                    }

                    // --- braces ---
                    cases?.accept(i)
                    when (c) {
                        '{', '(', '[' -> {
                            val color = stack.size % paletteSize
                            stack.addLast(OpenDelimiter(c, i, color))
                            if (i in rangeStart..rangeEnd) out.add(DelimiterMark(i, color))
                        }
                        '}', ')', ']' -> {
                            val open = if (stack.isNotEmpty() && matches(stack.last().ch, c)) stack.removeLast() else null
                            if (open != null) {
                                pairs[open.offset] = DelimiterPair(open.offset, i, open.colorIndex)
                                pairs[i] = DelimiterPair(i, open.offset, open.colorIndex)
                                if (i in rangeStart..rangeEnd) out.add(DelimiterMark(i, open.colorIndex))
                            }
                        }
                    }

                    i++
                }

                Mode.LINE_COMMENT -> {
                    if (c == '\n') mode = Mode.CODE
                    i++
                }

                Mode.BLOCK_COMMENT -> {
                    if (c == '*' && n == '/') { mode = Mode.CODE; i += 2 } else i++
                }

                Mode.STRING -> {
                    // Consume an escape and its payload together so an escaped quote cannot end the literal.
                    if (c == '\\') { i = min(scanEnd, i + 2); continue } // escape
                    if (c == '"') mode = Mode.CODE
                    i++
                }

                Mode.CHAR -> {
                    if (c == '\\') { i = min(scanEnd, i + 2); continue }
                    if (c == '\'') mode = Mode.CODE
                    i++
                }

                Mode.CS_VERBATIM -> {
                    // C# verbatim: "" = escaped quote
                    if (c == '"' && n == '"') { i += 2; continue }
                    if (c == '"') mode = Mode.CODE
                    i++
                }

                Mode.RUST_RAW -> {
                    // A quote closes a raw string only when followed by the opening delimiter's
                    // hash count. Quotes with fewer hashes are still part of the string contents.
                    if (c == '"') {
                        var ok = true
                        var k = 0
                        while (k < rustRawHashes) {
                            if (i + 1 + k >= scanEnd || text[i + 1 + k] != '#') { ok = false; break }
                            k++
                        }
                        if (ok) {
                            mode = Mode.CODE
                            i += 1 + rustRawHashes
                            continue
                        }
                    }
                    i++
                }
            }
        }

        return DelimiterScan(out, pairs, cases?.finish(pairs).orEmpty())
    }

    fun isDelimiter(ch: Char): Boolean = ch == '{' || ch == '}' || ch == '(' || ch == ')' || ch == '[' || ch == ']'
}
