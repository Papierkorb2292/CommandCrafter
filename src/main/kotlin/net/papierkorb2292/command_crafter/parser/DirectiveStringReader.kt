package net.papierkorb2292.command_crafter.parser

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.StringReader
import net.minecraft.server.command.ServerCommandSource
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.mixin.parser.StringReaderAccessor
import net.papierkorb2292.command_crafter.parser.helper.ProcessedInputCursorMapper
import java.util.*
import kotlin.math.min

class DirectiveStringReader<out ResourceCreator>(
    var lines: List<String>,
    val dispatcher: CommandDispatcher<ServerCommandSource>,
    val resourceCreator: ResourceCreator,
) : StringReader(""),
    LineAwareStringReader {

    private val directiveManager = DirectiveManager()

    private var nextLine: Int = 0

    var readCharacters = 0
    var absoluteCursor
        get() = cursor + readCharacters
        set(value) { cursor = value - readCharacters }
    override val currentLine
        get() = AnalyzingResult.getPositionFromCursor(absoluteCursor, lines, zeroBased = false).line
    var onlyReadEscapedMultiline = false
        set(value) {
            if(value == field) return
            field = value
            if(value) {
                if(string.endsWith('\n'))
                    setString(string.substring(0, string.length - 1))
            } else if(nextLine < lines.size) {
                setString(string + '\n')
            }
        }
    var escapedMultilineCursorMapper: ProcessedInputCursorMapper? = null

    private fun extendToLengthFromCursor(length: Int): Boolean {
        if(onlyReadEscapedMultiline) {
            val firstLineMappingMissing = escapedMultilineCursorMapper?.let { it.prevSourceEnd <= absoluteCursor } == true
            if(!string.endsWith('\\')) {
                if(firstLineMappingMissing) {
                    escapedMultilineCursorMapper?.addMapping(absoluteCursor, cursor, remainingLength)
                }
                return super.canRead(length)
            }
            if(firstLineMappingMissing) {
                escapedMultilineCursorMapper?.addMapping(absoluteCursor, cursor, remainingLength - 1)
            }
            while(true) {
                if(nextLine >= lines.size)
                    throw IllegalArgumentException("Line continuation at end of file")
                setString(string.substring(0, string.length - 1))
                val line = lines[nextLine++]
                val indent = line.indexOfFirst { !it.isWhitespace() }
                if(indent == -1) {
                    readCharacters += 2
                    break
                }
                val contentEnd = line.indexOfLast { !it.isWhitespace() }
                val trimmed = line.substring(indent, contentEnd + 1)
                val hasBackslash = trimmed.endsWith('\\')
                escapedMultilineCursorMapper?.addFollowingMapping(
                    readCharacters + string.length + indent + 2,
                    if(hasBackslash) trimmed.length - 1 else trimmed.length
                )
                setString(string + trimmed)
                readCharacters += line.length - trimmed.length + 2
                if(!hasBackslash)
                    break
            }
            return super.canRead(length)
        }

        while(!super.canRead(length)) {
            if(nextLine >= lines.size) {
                return false
            }
            setString(string + lines[nextLine++])
            if(nextLine < lines.size) {
                setString(string + '\n')
            }
        }
        return true
    }

    override fun canRead(length: Int) = extendToLengthFromCursor(length)

    override fun read(): Char {
        if(!extendToLengthFromCursor(1)) {
            throw IndexOutOfBoundsException(cursor)
        }
        return super.read()
    }

    override fun peek(): Char {
        if(!extendToLengthFromCursor(1)) {
            throw IndexOutOfBoundsException(cursor)
        }
        return super.peek()
    }

    override fun peek(offset: Int): Char {
        if(!extendToLengthFromCursor(offset + 1)) {
            throw IndexOutOfBoundsException(cursor + offset)
        }
        return super.peek(offset)
    }

    fun toCompleted() {
        setString(string.substring(0, min(cursor, if(string.endsWith('\n')) string.length - 1 else string.length)))
        nextLine = lines.size
    }

    fun readLine(): String {
        val startCursor = cursor
        while(canRead() && peek() != '\n') {
            skip()
        }
        val result = string.substring(startCursor, cursor)
        if(canRead()) {
            skip() // Skip new line
        }
        return result
    }

    fun cutReadChars() {
        extendToLengthFromCursor(0)
        setString(string.substring(min(cursor, string.length)))
        readCharacters += cursor
        cursor = 0
    }

    fun endStatement() {
        cutReadChars()
        while(true) {
            val foundDirective = trySkipWhitespace {
                if(canRead() && peek() == '@') {
                    skip()
                    directiveManager.readDirective(this)
                    return@trySkipWhitespace true
                }
                false
            }
            if(!foundDirective) {
                break
            }
        }
        scopeStack.element().closure.let {
            if(it.endsClosure(this)) {
                it.skipClosureEnd(this)
                scopeStack.poll()
                currentLanguage = null
            }
        }
    }

    fun endStatementAndAnalyze(analyzingResult: AnalyzingResult) {
        extendToLengthFromCursor(0)
        setString(string.substring(min(cursor, string.length)))
        readCharacters += cursor
        cursor = 0
        while(true) {
            val foundDirective = trySkipWhitespace {
                if(canRead() && peek() == '@') {
                    skip()
                    directiveManager.readDirectiveAndAnalyze(this, analyzingResult)
                    return@trySkipWhitespace true
                }
                false
            }
            if(!foundDirective) {
                break
            }
        }
        scopeStack.element().closure.let {
            if(it.endsClosure(this)) {
                it.skipClosureEnd(this)
                scopeStack.poll()
                currentLanguage = null
            }
        }
    }

    var currentIndentation: Int private set
    init { currentIndentation = 0 }

    fun saveIndentation() {
        currentIndentation = readIndentation()
    }

    fun readIndentation(): Int {
        var indent = 0
        while(canRead() && peek() == ' ') {
            skip()
            indent++
        }
        return indent
    }

    inline fun tryReadIndentation(predicate: (Int) -> Boolean): Boolean {
        val startCursor = cursor
        val indent = readIndentation()
        if(predicate(indent)) {
            return true
        }
        cursor = startCursor
        return false
    }

    inline fun trySkipWhitespace(skipNewLine: Boolean = true, reader: () -> Boolean): Boolean {
        val startCursor = cursor
        skipWhitespace(skipNewLine)
        if(!reader()) {
            cursor = startCursor
            return false
        }
        return true
    }

    inline fun <Result> withNoMultilineRestriction(reader: (DirectiveStringReader<ResourceCreator>) -> Result): Result {
        val prevOnlyReadEscapedMultiline = onlyReadEscapedMultiline
        val prevCursorMapper = escapedMultilineCursorMapper
        onlyReadEscapedMultiline = false
        escapedMultilineCursorMapper = null
        try {
            return reader(this)
        } finally {
            onlyReadEscapedMultiline = prevOnlyReadEscapedMultiline
            escapedMultilineCursorMapper = prevCursorMapper
        }
    }

    val scopeStack: Deque<Scope> = LinkedList()

    var currentLanguage: Language? = null

    val closureDepth get() = scopeStack.size

    fun enterClosure(closure: Language.LanguageClosure) {
        scopeStack.addFirst(Scope(closure, currentLine, closure.startLanguage))
        currentLanguage = closure.startLanguage
    }

    fun updateLanguage() {
        currentLanguage = scopeStack.peek()?.language
    }

    fun switchLanguage(language: Language) {
        currentLanguage = null
        scopeStack.peek().language = language
    }

    fun copy() : DirectiveStringReader<ResourceCreator> {
        return DirectiveStringReader(lines, dispatcher, resourceCreator).also {
            it.setString(string)
            it.cursor = cursor
            it.scopeStack.addAll(scopeStack)
            it.updateLanguage()
            it.readCharacters = readCharacters
            it.currentIndentation = currentIndentation
            it.nextLine = nextLine
            it.onlyReadEscapedMultiline = onlyReadEscapedMultiline
            it.escapedMultilineCursorMapper = escapedMultilineCursorMapper
        }
    }

    fun copyFrom(other: DirectiveStringReader<*>) {
        cursor = other.cursor
        readCharacters = other.readCharacters
        setString(other.string)
        nextLine = other.nextLine
    }

    fun onlyCurrentLine() : DirectiveStringReader<ResourceCreator> {
        while(canRead() && peek() != '\n')
            skip()
        val line = string.substring(0, cursor)
        if(canRead())
            skip() // Skip new line
        return DirectiveStringReader(listOf(line), dispatcher, resourceCreator).also {
            it.scopeStack.addAll(scopeStack)
            it.cursor = cursor
            it.readCharacters = readCharacters
        }
    }

    fun skipTo(other: DirectiveStringReader<*>) {
        absoluteCursor = other.absoluteCursor
        withNoMultilineRestriction { it.extendToLengthFromCursor(0) }
    }

    override fun skipWhitespace() {
        skipWhitespace(true)
    }

    fun skipWhitespace(skipNewLine: Boolean) {
        while(canRead() && Character.isWhitespace(peek())) {
            if(peek() == '\n') {
                if (!skipNewLine)
                    break
            }
            skip()
        }
    }

    fun skipSpaces() {
        while(canRead() && peek() == ' ') read()
    }

    private fun setString(string: String) {
        @Suppress("KotlinConstantConditions")
        (this as StringReaderAccessor).setString(string)
    }

    class Scope(val closure: Language.LanguageClosure, val startLine: Int, var language: Language)
}

