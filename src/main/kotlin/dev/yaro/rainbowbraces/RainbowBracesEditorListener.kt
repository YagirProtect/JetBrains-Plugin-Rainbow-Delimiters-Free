package dev.yaro.rainbowbraces

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.tree.TokenSet
import com.intellij.util.Alarm
import com.intellij.openapi.util.Disposer
import java.awt.Font
import java.awt.Point
import kotlin.math.max
import kotlin.math.min

private data class Mark(val offset: Int, val colorIndex: Int)
private data class Open(val ch: Char, val offset: Int, val colorIndex: Int)
private data class PairMark(val offset: Int, val pairedOffset: Int, val colorIndex: Int)
private data class DelimiterCache(
    val marks: List<Mark> = emptyList(),
    val pairsByOffset: Map<Int, PairMark> = emptyMap()
)

private data class EditorState(
    val highlighters: MutableList<RangeHighlighter>,
    val emphasisHighlighters: MutableList<RangeHighlighter>,
    val alarm: Alarm,
    val disposable: Disposable,
    val docListener: DocumentListener,
    val visibleAreaListener: VisibleAreaListener,
    val caretListener: CaretListener,
    var cachedDocumentStamp: Long = -1,
    var cachedPaletteSize: Int = -1,
    var cachedDelimiters: DelimiterCache = DelimiterCache()
)

class RainbowBracesEditorListener : EditorFactoryListener {

    companion object {
        private val LOG = Logger.getInstance(RainbowBracesEditorListener::class.java)
        private val STATE_KEY = Key.create<EditorState>("dev.yaro.rainbowbraces.state")

        private const val MAX_FILE_CHARS = 500_000
        private const val MAX_LEXER_CHARS = 250_000
        private const val MAX_VISIBLE_HIGHLIGHTERS = 2_000
        private const val MARGIN = 3000
        private const val UPDATE_DELAY_MS = 150

        fun refreshEditor(editor: Editor) {
            val state = editor.getUserData(STATE_KEY) ?: return
            state.cachedDocumentStamp = -1
            RainbowBracesEditorListener().update(editor, state)
        }
    }

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        if (!shouldEnable(editor)) return

        val disposable = Disposer.newDisposable("Rainbow Delimiters editor state")
        val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, disposable)
        val highlighters = mutableListOf<RangeHighlighter>()
        val emphasisHighlighters = mutableListOf<RangeHighlighter>()

        val docListener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                scheduleUpdate(editor, alarm)
            }
        }

        val visibleAreaListener = VisibleAreaListener {
            scheduleUpdate(editor, alarm)
        }

        val caretListener = object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                updatePairEmphasis(editor)
            }
        }

        editor.document.addDocumentListener(docListener, disposable)
        editor.scrollingModel.addVisibleAreaListener(visibleAreaListener)
        editor.caretModel.addCaretListener(caretListener)

        val state = EditorState(
            highlighters,
            emphasisHighlighters,
            alarm,
            disposable,
            docListener,
            visibleAreaListener,
            caretListener
        )
        editor.putUserData(STATE_KEY, state)

        scheduleUpdate(editor, alarm)
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        val editor = event.editor
        val state = editor.getUserData(STATE_KEY) ?: return

        editor.scrollingModel.removeVisibleAreaListener(state.visibleAreaListener)
        editor.caretModel.removeCaretListener(state.caretListener)

        Disposer.dispose(state.disposable)
        state.highlighters.forEach { editor.markupModel.removeHighlighter(it) }
        state.highlighters.clear()
        clearPairEmphasis(editor, state)

        editor.putUserData(STATE_KEY, null)
    }

    private fun shouldEnable(editor: Editor): Boolean {
        if (editor is EditorEx && editor.isDisposed) return false
        val doc = editor.document
        if (doc.textLength > MAX_FILE_CHARS) return false

        val vf = FileDocumentManager.getInstance().getFile(doc) ?: return false
        val ext = vf.extension?.lowercase() ?: return false
        if (!RainbowSettingsService.getInstance().isEnabledForExtension(ext)) return false

        return true
    }

    private fun scheduleUpdate(editor: Editor, alarm: Alarm) {
        if (editor is EditorEx && editor.isDisposed) return

        alarm.cancelAllRequests()
        alarm.addRequest(
            {
                val state = editor.getUserData(STATE_KEY) ?: return@addRequest
                ApplicationManager.getApplication().invokeLater {
                    if (editor is EditorEx && editor.isDisposed) return@invokeLater
                    update(editor, state)
                }
            },
            UPDATE_DELAY_MS
        )
    }

    private fun update(editor: Editor, state: EditorState) {
        try {
            if (!shouldEnable(editor)) {
                clearHighlighters(editor, state)
                return
            }

            val doc = editor.document
            val (rangeStart, rangeEnd) = visibleOffsets(editor, doc.textLength)

            val palette = RainbowSettingsService.getInstance().activeColors()
            val marks = cachedDelimiters(editor, state, palette.size).marks
                .asSequence()
                .filter { it.offset in rangeStart..rangeEnd }
                .take(MAX_VISIBLE_HIGHLIGHTERS)
                .toList()

            clearHighlighters(editor, state)

            for (m in marks) {
                val attrs = TextAttributes(
                    palette[m.colorIndex % palette.size],
                    null, null, null,
                    Font.PLAIN
                )
                val rh = editor.markupModel.addRangeHighlighter(
                    m.offset,
                    m.offset + 1,
                    HighlighterLayer.ADDITIONAL_SYNTAX,
                    attrs,
                    HighlighterTargetArea.EXACT_RANGE
                )
                state.highlighters.add(rh)
            }
            updatePairEmphasis(editor)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (t: Throwable) {
            LOG.warn("Rainbow delimiters update failed", t)
            clearHighlighters(editor, state)
        }
    }

    private fun cachedDelimiters(editor: Editor, state: EditorState, paletteSize: Int): DelimiterCache {
        val stamp = editor.document.modificationStamp
        if (state.cachedDocumentStamp == stamp && state.cachedPaletteSize == paletteSize) {
            return state.cachedDelimiters
        }

        val delimiters = computeMarksPreferLexer(editor, 0, editor.document.textLength, paletteSize)
        state.cachedDocumentStamp = stamp
        state.cachedPaletteSize = paletteSize
        state.cachedDelimiters = delimiters
        return delimiters
    }

    private fun clearHighlighters(editor: Editor, state: EditorState) {
        state.highlighters.forEach { editor.markupModel.removeHighlighter(it) }
        state.highlighters.clear()
        clearPairEmphasis(editor, state)
    }

    private fun updatePairEmphasis(editor: Editor) {
        val state = editor.getUserData(STATE_KEY) ?: return
        try {
            if (!shouldEnable(editor)) {
                clearPairEmphasis(editor, state)
                return
            }
            if (!RainbowSettingsService.getInstance().isPairEmphasisEnabled()) {
                clearPairEmphasis(editor, state)
                return
            }

            val palette = RainbowSettingsService.getInstance().activeColors()
            val cache = cachedDelimiters(editor, state, palette.size)
            val offset = matchingCandidateOffset(editor)
            val pair = offset?.let { cache.pairsByOffset[it] }
            clearPairEmphasis(editor, state)
            if (pair == null) return

            val color = palette[pair.colorIndex % palette.size]
            state.emphasisHighlighters.add(addEmphasisHighlighter(editor, pair.offset, color))
            state.emphasisHighlighters.add(addEmphasisHighlighter(editor, pair.pairedOffset, color))
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (t: Throwable) {
            LOG.warn("Rainbow delimiters pair emphasis failed", t)
            clearPairEmphasis(editor, state)
        }
    }

    private fun matchingCandidateOffset(editor: Editor): Int? {
        val doc = editor.document
        val caretOffset = editor.caretModel.offset
        if (caretOffset < doc.textLength && isDelimiter(doc.charsSequence[caretOffset])) return caretOffset
        val previous = caretOffset - 1
        if (previous >= 0 && isDelimiter(doc.charsSequence[previous])) return previous
        return null
    }

    private fun addEmphasisHighlighter(editor: Editor, offset: Int, color: java.awt.Color): RangeHighlighter {
        val attrs = TextAttributes(
            color,
            null,
            color,
            EffectType.ROUNDED_BOX,
            Font.BOLD
        )
        return editor.markupModel.addRangeHighlighter(
            offset,
            offset + 1,
            HighlighterLayer.SELECTION - 1,
            attrs,
            HighlighterTargetArea.EXACT_RANGE
        )
    }

    private fun clearPairEmphasis(editor: Editor, state: EditorState) {
        state.emphasisHighlighters.forEach { editor.markupModel.removeHighlighter(it) }
        state.emphasisHighlighters.clear()
    }

    private fun visibleOffsets(editor: Editor, docLen: Int): Pair<Int, Int> {
        val area = editor.scrollingModel.visibleArea
        val p1 = Point(area.x, area.y)
        val p2 = Point(area.x + area.width, area.y + area.height)

        val start = editor.logicalPositionToOffset(editor.xyToLogicalPosition(p1))
        val end = editor.logicalPositionToOffset(editor.xyToLogicalPosition(p2))

        val a = max(0, start - MARGIN)
        val b = min(docLen, end + MARGIN)
        return a to b
    }

    private fun computeMarksPreferLexer(editor: Editor, rangeStart: Int, rangeEnd: Int, paletteSize: Int): DelimiterCache {
        val doc = editor.document
        val text = doc.charsSequence
        val scanEnd = min(doc.textLength, rangeEnd)

        val project = editor.project
        val vf = FileDocumentManager.getInstance().getFile(doc)
        if (project == null || vf == null || scanEnd > MAX_LEXER_CHARS) {
            return computeMarksManual(text, scanEnd, rangeStart, rangeEnd, paletteSize)
        }

        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(doc)
            ?: return computeMarksManual(text, scanEnd, rangeStart, rangeEnd, paletteSize)

        val language = psiFile.viewProvider.baseLanguage
        val parserDef = LanguageParserDefinitions.INSTANCE.forLanguage(language)
        val commentTokens: TokenSet = parserDef?.commentTokens ?: TokenSet.EMPTY
        val stringTokens: TokenSet = parserDef?.stringLiteralElements ?: TokenSet.EMPTY

        val highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(language, project, vf)
            ?: return computeMarksManual(text, scanEnd, rangeStart, rangeEnd, paletteSize)

        val lexer = highlighter.highlightingLexer
        lexer.start(text, 0, scanEnd)

        val out = ArrayList<Mark>(512)
        val pairs = HashMap<Int, PairMark>()
        val stack = ArrayDeque<Open>()

        while (lexer.tokenType != null) {
            val tt = lexer.tokenType!!
            val ts = lexer.tokenStart
            val te = lexer.tokenEnd

            val ignored = commentTokens.contains(tt) || stringTokens.contains(tt)
            if (!ignored) {
                val from = max(0, ts)
                val to = min(scanEnd, te)

                for (i in from until to) {
                    val c = text[i]
                    when (c) {
                        '{', '(', '[' -> {
                            val color = stack.size % paletteSize
                            stack.addLast(Open(c, i, color))
                            if (i in rangeStart..rangeEnd) out.add(Mark(i, color))
                        }
                        '}', ')', ']' -> {
                            val open = if (stack.isNotEmpty() && matches(stack.last().ch, c)) stack.removeLast() else null
                            if (open != null) {
                                pairs[open.offset] = PairMark(open.offset, i, open.colorIndex)
                                pairs[i] = PairMark(i, open.offset, open.colorIndex)
                                if (i in rangeStart..rangeEnd) out.add(Mark(i, open.colorIndex))
                            }
                        }
                    }
                }
            }

            lexer.advance()
        }

        return DelimiterCache(out, pairs)
    }

    private enum class Mode {
        CODE, LINE_COMMENT, BLOCK_COMMENT, STRING, CHAR, CS_VERBATIM, RUST_RAW
    }

    private fun computeMarksManual(
        text: CharSequence,
        scanEnd: Int,
        rangeStart: Int,
        rangeEnd: Int,
        paletteSize: Int
    ): DelimiterCache {
        val out = ArrayList<Mark>(512)
        val pairs = HashMap<Int, PairMark>()
        val stack = ArrayDeque<Open>()

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
                    when (c) {
                        '{', '(', '[' -> {
                            val color = stack.size % paletteSize
                            stack.addLast(Open(c, i, color))
                            if (i in rangeStart..rangeEnd) out.add(Mark(i, color))
                        }
                        '}', ')', ']' -> {
                            val open = if (stack.isNotEmpty() && matches(stack.last().ch, c)) stack.removeLast() else null
                            if (open != null) {
                                pairs[open.offset] = PairMark(open.offset, i, open.colorIndex)
                                pairs[i] = PairMark(i, open.offset, open.colorIndex)
                                if (i in rangeStart..rangeEnd) out.add(Mark(i, open.colorIndex))
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
                    // end: " + hashes
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

        return DelimiterCache(out, pairs)
    }

    private fun isDelimiter(ch: Char): Boolean = ch == '{' || ch == '}' || ch == '(' || ch == ')' || ch == '[' || ch == ']'

    private fun matches(open: Char, close: Char): Boolean = when (open) {
        '{' -> close == '}'
        '(' -> close == ')'
        '[' -> close == ']'
        else -> false
    }
}
