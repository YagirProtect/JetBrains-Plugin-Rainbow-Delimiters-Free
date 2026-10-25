// Copyright (c) 2026 YagirProtect.
// Licensed under GPL-3.0-only with the JetBrains IDE Linking Exception.
// See LICENSE and LICENSE-EXCEPTION in the project root.

package dev.yaro.rainbowbraces.scanning

/**
 * Half-open structural range for an unbraced switch section.
 * [labelOffset] points to case/default, [bodyOffset] is immediately after the header colon,
 * and [endOffset] points to the next label or the switch's closing brace, neither of which
 * belongs to the body. The renderer converts these offsets to visual rows when painting.
 */
internal data class SwitchCaseSection(val labelOffset: Int, val bodyOffset: Int, val endOffset: Int)

/**
 * Finds C-family switch sections using code characters classified by [DelimiterScanner].
 *
 * This collector does not run a second string/comment parser. Its offsets may be discontinuous
 * wherever the caller skipped a literal or comment. Matched bracket ranges let it skip nested
 * expressions, property patterns, and blocks without requiring a product-specific PSI API.
 * Build one collector per scan; [finish] consumes its buffered tokens and returns detached ranges.
 */
internal class SwitchCaseScanner(private val text: CharSequence) {
    private data class Token(val value: String, val offset: Int)
    private data class Label(val tokenIndex: Int, val colonIndex: Int)

    private val tokens = ArrayList<Token>()
    private val word = StringBuilder()
    private var wordStart = 0
    private var previousOffset = -2

    companion object {
        private val EXTENSIONS = setOf(
            "cs", "c", "h", "cc", "cpp", "cxx", "hpp", "hxx", "hh", "inl",
            "ixx", "mxx", "cppm", "ccm", "cxxm", "c++m"
        )

        fun supportsExtension(extension: String?): Boolean = extension?.lowercase() in EXTENSIONS
    }

    fun accept(offset: Int) {
        // A skipped comment must separate words: "ca/*...*/se" must not become the keyword "case".
        if (offset != previousOffset + 1) flushWord()
        previousOffset = offset
        val char = text[offset]
        if (char.isLetterOrDigit() || char == '_') {
            if (word.isEmpty()) wordStart = offset
            word.append(char)
        } else {
            flushWord()
            if (!char.isWhitespace()) tokens.add(Token(char.toString(), offset))
        }
    }

    fun finish(pairs: Map<Int, DelimiterPair>): List<SwitchCaseSection> {
        flushWord()
        val sections = ArrayList<SwitchCaseSection>()
        for (index in tokens.indices) {
            // Requiring switch (...) { ... } excludes C# switch expressions using pattern => value.
            // Every switch is visited here, including nested ones; collectSections skips nested bodies
            // only while finding the labels owned by its particular enclosing switch.
            if (tokens[index].value != "switch" || tokens.getOrNull(index + 1)?.value != "(") continue
            val condition = pairs[tokens[index + 1].offset] ?: continue
            val bodyIndex = tokenIndex(condition.pairedOffset) + 1
            if (tokens.getOrNull(bodyIndex)?.value != "{") continue
            val body = pairs[tokens[bodyIndex].offset] ?: continue
            val endIndex = tokenIndex(body.pairedOffset)
            collectSections(bodyIndex + 1, endIndex, pairs, sections)
        }
        return sections.sortedBy { it.labelOffset }
    }

    /** Collect only direct-child labels; nested bracket groups have their own structural owner. */
    private fun collectSections(
        start: Int, end: Int, pairs: Map<Int, DelimiterPair>, sections: MutableList<SwitchCaseSection>
    ) {
        val labels = ArrayList<Label>()
        var index = start
        while (index < end) {
            val token = tokens[index]
            val previous = tokens.getOrNull(index - 1)?.value
            // "goto case/default" references a label rather than starting a new section.
            // The other exclusions avoid treating escaped/member identifiers as label keywords.
            if ((token.value == "case" || token.value == "default") &&
                previous != "goto" && previous != "@" && previous != ".") {
                val colon = labelColon(index, end, pairs)
                if (colon != null) {
                    labels.add(Label(index, colon))
                    index = colon + 1
                    continue
                }
            }
            index = afterGroup(index, pairs) ?: (index + 1)
        }
        labels.forEachIndexed { labelIndex, label ->
            val nextIndex = labels.getOrNull(labelIndex + 1)?.tokenIndex ?: end
            val firstBody = label.colonIndex + 1
            // Empty stacked labels share the following label's guide. Explicit
            // blocks already have a colored brace guide, so do not add a second one.
            if (firstBody >= nextIndex || tokens[firstBody].value == "{") return@forEachIndexed
            sections.add(SwitchCaseSection(
                tokens[label.tokenIndex].offset, tokens[label.colonIndex].offset + 1, tokens[nextIndex].offset
            ))
        }
    }

    /**
     * Find the label separator rather than a colon inside a pattern or guard expression.
     * Groups are skipped using the bracket map; top-level ternaries require their own counter.
     * Incomplete labels return null instead of borrowing a separator from a later statement.
     */
    private fun labelColon(start: Int, end: Int, pairs: Map<Int, DelimiterPair>): Int? {
        if (tokens[start].value == "default") return (start + 1).takeIf { tokens.getOrNull(it)?.value == ":" }
        var index = start + 1
        var conditionalDepth = 0
        while (index < end) {
            val token = tokens[index]
            val afterGroup = afterGroup(index, pairs)
            if (afterGroup != null) {
                index = afterGroup
                continue
            }
            when (token.value) {
                ";", "case" -> return null
                "?" -> {
                    // C# ??, ?. and ?[ are not ternary operators and must not consume a later colon.
                    val next = text.getOrNull(token.offset + 1)
                    val previous = text.getOrNull(token.offset - 1)
                    if (next != '?' && next != '.' && next != '[' && previous != '?') conditionalDepth++
                }
                ":" -> {
                    // C++ enum cases may use qualified names such as Namespace::Kind::Ready.
                    if (text.getOrNull(token.offset - 1) != ':' && text.getOrNull(token.offset + 1) != ':') {
                        if (conditionalDepth == 0) return index
                        conditionalDepth--
                    }
                }
            }
            index++
        }
        return null
    }

    private fun afterGroup(index: Int, pairs: Map<Int, DelimiterPair>): Int? {
        // Token offsets remain sorted because accept receives source characters in document order.
        // Binary search avoids walking the contents of a group for each enclosing switch.
        val value = tokens[index].value
        if (value != "(" && value != "[" && value != "{") return null
        val pair = pairs[tokens[index].offset] ?: return null
        return (tokenIndex(pair.pairedOffset) + 1).takeIf { it > index }
    }

    private fun tokenIndex(offset: Int): Int = tokens.binarySearchBy(offset) { it.offset }

    private fun flushWord() {
        if (word.isEmpty()) return
        tokens.add(Token(word.toString(), wordStart))
        word.setLength(0)
    }
}
