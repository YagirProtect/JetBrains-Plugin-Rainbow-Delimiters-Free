package dev.yaro.rainbowbraces

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.tree.TokenSet
import com.intellij.ui.JBColor
import com.intellij.util.Alarm
import java.awt.Color
import java.awt.Font
import java.awt.Point
import kotlin.math.max
import kotlin.math.min

private data class EditorState(
    val highlighters: MutableList<RangeHighlighter>,
    val alarm: Alarm,
    val docListener: DocumentListener,
    val visibleAreaListener: VisibleAreaListener
)

class RainbowBracesEditorListener : EditorFactoryListener {

    companion object {
        private val STATE_KEY = Key.create<EditorState>("dev.yaro.rainbowbraces.state")

        private val ENABLED_EXT = setOf(
            "rs", "cs",
            "java", "kt", "kts",
            "c", "h", "cc", "cpp", "cxx", "hpp", "hh", "hxx", "ixx", "mxx", "cppm", "ccm", "cxxm", "c++m"
            "js", "ts", "jsx", "tsx",
            "py", "go", "swift", "lua", "json", "hlsl", "shader", "php", "go",
            "glsl", "vert", "vsh", "tesc", "tese", "geom", "gsh", "frag", "fsh", "comp"
        )

        private val PALETTE: Array<Color> = arrayOf(
            JBColor(Color(0xC62828), Color(0xFF6B6B)),
            JBColor(Color(0xAD1457), Color(0xFF4D9D)),
            JBColor(Color(0x6A1B9A), Color(0xB388FF)),
            JBColor(Color(0x283593), Color(0x82B1FF)),
            JBColor(Color(0x1565C0), Color(0x4FC3F7)),
            JBColor(Color(0x00695C), Color(0x64FFDA)),
            JBColor(Color(0x2E7D32), Color(0xB9F6CA)),
            JBColor(Color(0xF9A825), Color(0xFFE082)),
        )

        private const val MAX_FILE_CHARS = 1_500_000
        private const val MARGIN = 6000
        private const val UPDATE_DELAY_MS = 80
    }

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        if (!shouldEnable(editor)) return

        val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD)
        val highlighters = mutableListOf<RangeHighlighter>()

        val docListener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                scheduleUpdate(editor, alarm)
            }
        }

        val visibleAreaListener = VisibleAreaListener {
            scheduleUpdate(editor, alarm)
        }

        editor.document.addDocumentListener(docListener)
        editor.scrollingModel.addVisibleAreaListener(visibleAreaListener)

        val state = EditorState(highlighters, alarm, docListener, visibleAreaListener)
        editor.putUserData(STATE_KEY, state)

        scheduleUpdate(editor, alarm)
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        val editor = event.editor
        val state = editor.getUserData(STATE_KEY) ?: return

        editor.document.removeDocumentListener(state.docListener)
        editor.scrollingModel.removeVisibleAreaListener(state.visibleAreaListener)

        state.alarm.cancelAllRequests()
        state.highlighters.forEach { editor.markupModel.removeHighlighter(it) }
        state.highlighters.clear()

        editor.putUserData(STATE_KEY, null)
    }

    private fun shouldEnable(editor: Editor): Boolean {
        if (editor is EditorEx && editor.isDisposed) return false
        val doc = editor.document
        if (doc.textLength > MAX_FILE_CHARS) return false

        val vf = FileDocumentManager.getInstance().getFile(doc) ?: return false
        val ext = vf.extension?.lowercase() ?: return false
        if (ext !in ENABLED_EXT) return false

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
        if (!shouldEnable(editor)) return

        val doc = editor.document
        val (rangeStart, rangeEnd) = visibleOffsets(editor, doc.textLength)

        val marks = computeMarksPreferLexer(editor, rangeStart, rangeEnd)

        state.highlighters.forEach { editor.markupModel.removeHighlighter(it) }
        state.highlighters.clear()

        for (m in marks) {
            val attrs = TextAttributes(
                PALETTE[m.colorIndex % PALETTE.size],
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

    private data class Mark(val offset: Int, val colorIndex: Int)
    private data class Open(val ch: Char, val colorIndex: Int)

    private fun computeMarksPreferLexer(editor: Editor, rangeStart: Int, rangeEnd: Int): List<Mark> {
        val doc = editor.document
        val text = doc.charsSequence
        val scanEnd = min(doc.textLength, rangeEnd)

        val project = editor.project
        val vf = FileDocumentManager.getInstance().getFile(doc)
        if (project == null || vf == null) {
            // чисто ручной режим
            return computeMarksManual(text, scanEnd, rangeStart, rangeEnd)
        }

        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(doc)
            ?: return computeMarksManual(text, scanEnd, rangeStart, rangeEnd)

        val language = psiFile.viewProvider.baseLanguage
        val parserDef = LanguageParserDefinitions.INSTANCE.forLanguage(language)
        val commentTokens: TokenSet = parserDef?.commentTokens ?: TokenSet.EMPTY
        val stringTokens: TokenSet = parserDef?.stringLiteralElements ?: TokenSet.EMPTY

        val highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(language, project, vf)
            ?: return computeMarksManual(text, scanEnd, rangeStart, rangeEnd)

        val lexer = highlighter.highlightingLexer
        lexer.start(text, 0, scanEnd)

        val out = ArrayList<Mark>(512)
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
                            val color = stack.size % PALETTE.size
                            stack.addLast(Open(c, color))
                            if (i in rangeStart..rangeEnd) out.add(Mark(i, color))
                        }
                        '}', ')', ']' -> {
                            val open = if (stack.isNotEmpty() && matches(stack.last().ch, c)) stack.removeLast() else null
                            if (open != null && i in rangeStart..rangeEnd) out.add(Mark(i, open.colorIndex))
                        }
                    }
                }
            }

            lexer.advance()
        }

        return out
    }

    private enum class Mode {
        CODE, LINE_COMMENT, BLOCK_COMMENT, STRING, CHAR, CS_VERBATIM, RUST_RAW
    }

    private fun computeMarksManual(text: CharSequence, scanEnd: Int, rangeStart: Int, rangeEnd: Int): List<Mark> {
        val out = ArrayList<Mark>(512)
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
                            val color = stack.size % PALETTE.size
                            stack.addLast(Open(c, color))
                            if (i in rangeStart..rangeEnd) out.add(Mark(i, color))
                        }
                        '}', ')', ']' -> {
                            val open = if (stack.isNotEmpty() && matches(stack.last().ch, c)) stack.removeLast() else null
                            if (open != null && i in rangeStart..rangeEnd) out.add(Mark(i, open.colorIndex))
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

        return out
    }

    private fun matches(open: Char, close: Char): Boolean = when (open) {
        '{' -> close == '}'
        '(' -> close == ')'
        '[' -> close == ']'
        else -> false
    }
}
