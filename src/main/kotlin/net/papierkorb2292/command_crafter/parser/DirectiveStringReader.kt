package net.papierkorb2292.command_crafter.parser

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.StringReader
import net.minecraft.command.CommandSource
import net.papierkorb2292.command_crafter.editor.OpenFile
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.mixin.parser.StringReaderAccessor
import java.io.IOException
import java.io.Reader
import java.util.*
import kotlin.math.min

class DirectiveStringReader<out ResourceCreator>(
    val fileMappingInfo: FileMappingInfo,
    val dispatcher: CommandDispatcher<CommandSource>,
    val resourceCreator: ResourceCreator,
) : StringReader(""),
    LineAwareStringReader {

    val lines by fileMappingInfo::lines
    val cursorMapper by fileMappingInfo::cursorMapper
    var readCharacters by fileMappingInfo::readCharacters
    var skippedChars by fileMappingInfo::skippedChars
    val readSkippingChars by fileMappingInfo::readSkippingChars

    val directiveManager = DirectiveManager()

    private var nextLine: Int = 0
    val remainingLengthWithoutNewline get() = remainingLength - if(string.endsWith('\n')) 1 else 0

    val nextLineEnd: Int get() {
        val index = string.indexOf('\n', cursor)
        return if(index != -1) index else string.length
    }

    var absoluteCursor
        get() = cursor + readCharacters
        set(value) { cursor = value - readCharacters }
    var skippingCursor
        get() = cursor + readSkippingChars
        set(value) { cursor = value - readSkippingChars }
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


    private fun extendToLengthFromCursor(length: Int): Boolean {
        if(onlyReadEscapedMultiline) {
            val firstLineMappingMissing = cursorMapper.prevTargetEnd <= skippingCursor
            if(!string.endsWith('\\')) {
                if(super.canRead(1) && firstLineMappingMissing)
                    cursorMapper.addMapping(absoluteCursor, skippingCursor, remainingLength)
                return super.canRead(length)
            }
            if(firstLineMappingMissing)
                cursorMapper.addMapping(absoluteCursor, skippingCursor, remainingLength - 1)
            while(true) {
                if(nextLine >= lines.size)
                    throw IllegalArgumentException("Line continuation at end of file")
                setString(string.substring(0, string.length - 1))
                val line = lines[nextLine++]
                val indent = line.indexOfFirst { !it.isWhitespace() }
                if(indent == -1) {
                    skippedChars += 2
                    readCharacters += 2
                    cursorMapper.addMapping(
                        readCharacters + string.length,
                        readSkippingChars + string.length,
                        0
                    )
                    break
                }
                val contentEnd = line.indexOfLast { !it.isWhitespace() }
                val hasBackslash = line[contentEnd] == '\\'
                // Only remove ending whitespace if it is after a backslash, such that in lines without a backslash suggestions still work at the end
                val trimmed = if(hasBackslash) line.substring(indent, contentEnd+1) else line.substring(indent)
                cursorMapper.addMapping(
                    readCharacters + string.length + indent + 2,
                    readSkippingChars + string.length,
                    if(hasBackslash) trimmed.length - 1 else trimmed.length
                )
                setString(string + trimmed)
                val skippedChars = line.length - trimmed.length + 2
                this.skippedChars += skippedChars
                readCharacters += skippedChars
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

    fun endStatement(skipNewLine: Boolean = true): Boolean {
        cutReadChars()
        val foundDirective = trySkipWhitespace(skipNewLine) {
            if(canRead() && peek() == '@') {
                skip()
                directiveManager.readDirective(this)
                true
            } else false
        }
        if(foundDirective)
            return true
        checkEndLanguage(skipNewLine)
        return false
    }

    fun checkEndLanguage(skipNewLine: Boolean = true) {
        val currentClosure = scopeStack.element().closure
        if(!currentClosure.endsClosure(this, skipNewLine)) return
        currentClosure.skipClosureEnd(this, skipNewLine)
        scopeStack.poll()
        currentLanguage = null
    }

    fun endStatementAndAnalyze(analyzingResult: AnalyzingResult, skipNewLine: Boolean = true): Boolean {
        cutReadChars()
        val foundDirective = trySkipWhitespace(skipNewLine) {
            if(canRead() && peek() == '@') {
                skip()
                directiveManager.readDirectiveAndAnalyze(this, analyzingResult)
                true
            } else false
        }
        if(foundDirective)
            return true
        checkEndLanguage(skipNewLine)
        return false
    }

    var currentIndentation: Int private set
    init { currentIndentation = 0 }

    fun saveIndentation() {
        currentIndentation = readIndentation()
    }

    fun readIndentation(): Int {
        val start = cursor
        while(canRead() && peek() == ' ') skip()
        return cursor - start
    }

    inline fun tryReadIndentation(predicate: (Int) -> Boolean): Boolean {
        val startCursor = cursor
        val indent = readIndentation()
        if(predicate(indent)) return true
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
        onlyReadEscapedMultiline = false
        try {
            return reader(this)
        } finally {
            onlyReadEscapedMultiline = prevOnlyReadEscapedMultiline
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
        return DirectiveStringReader(fileMappingInfo.copy(), dispatcher, resourceCreator).also {
            it.setString(string)
            it.cursor = cursor
            it.scopeStack.addAll(scopeStack)
            it.updateLanguage()
            it.readCharacters = readCharacters
            it.skippedChars = skippedChars
            it.currentIndentation = currentIndentation
            it.nextLine = nextLine
            it.onlyReadEscapedMultiline = onlyReadEscapedMultiline
        }
    }

    fun copyFrom(other: DirectiveStringReader<*>) {
        cursor = other.cursor
        readCharacters = other.readCharacters
        skippedChars = other.skippedChars
        setString(other.string)
        nextLine = other.nextLine
    }

    fun onlyCurrentLine() : DirectiveStringReader<ResourceCreator> {
        while(canRead() && peek() != '\n')
            skip()
        val line = string.substring(0, cursor)
        if(canRead())
            skip() // Skip new line
        return DirectiveStringReader(FileMappingInfo(listOf(line)), dispatcher, resourceCreator).also {
            it.scopeStack.addAll(scopeStack)
            it.cursor = cursor
            it.readCharacters = readCharacters
        }
    }

    fun skipTo(other: DirectiveStringReader<*>) {
        readCharacters += other.absoluteCursor - absoluteCursor
        skippedChars = other.skippedChars
        nextLine = other.nextLine
        setString(string.substring(0, cursor) + other.string.substring(other.cursor))
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

    fun getMultilineString(absoluteStart: Int, absoluteEnd: Int, lineSeparator: String = OpenFile.LINE_SEPARATOR): String {
        val startPos = AnalyzingResult.getPositionFromCursor(absoluteStart, lines)
        val endPos = AnalyzingResult.getPositionFromCursor(absoluteEnd, lines)
        val startLine = startPos.line
        val endLine = endPos.line
        val startColumn = startPos.character
        val endColumn = endPos.character
        if(startLine == endLine) {
            return lines[startLine].substring(startColumn, endColumn)
        }
        val result = StringBuilder()
        result.append(lines[startLine].substring(startColumn))
        for(i in startLine + 1 until endLine) {
            result.append(lineSeparator)
            result.append(lines[i])
        }
        result.append(lineSeparator)
        result.append(lines[endLine].substring(0, endColumn))
        return result.toString()
    }

    fun asReader(): Reader {
        return IoReader()
    }


    inner class IoReader : Reader() {
        private var isClosed = false

        override fun read(cbuf: CharArray, off: Int, len: Int): Int {
            if(isClosed) throw IOException("Stream closed")
            Objects.checkFromIndexSize(off, len, cbuf.size)
            if(len == 0) return 0
            extendToLengthFromCursor(len)
            if(!canRead()) return -1
            var bufIndex = 0
            while(canRead() && bufIndex < len) {
                cbuf[off + bufIndex++] = this@DirectiveStringReader.read()
            }
            return bufIndex
        }

        override fun close() {
            isClosed = true
        }
    }

    fun setString(string: String) {
        @Suppress("KotlinConstantConditions")
        (this as StringReaderAccessor)._setString(string)
    }

    class Scope(val closure: Language.LanguageClosure, val startLine: Int, var language: Language)
}

