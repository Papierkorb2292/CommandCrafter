package net.papierkorb2292.command_crafter.parser

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.StringReader
import net.minecraft.server.command.ServerCommandSource
import net.papierkorb2292.command_crafter.mixin.parser.StringReaderAccessor
import java.util.*
import kotlin.math.min

class DirectiveStringReader<out ResourceCreator>(
    var lines: List<String>,
    val dispatcher: CommandDispatcher<ServerCommandSource>,
    val resourceCreator: ResourceCreator
) : StringReader(""),
    LineAwareStringReader {

    private val directiveManager = DirectiveManager()

    private var nextLine: Int = 0

    var readCharacters = 0
    var absoluteCursor
        get() = cursor + readCharacters
        set(value) { cursor = value - readCharacters }
    override var currentLine = 1

    private fun extendToLengthFromCursor(length: Int): Boolean {
        while (!super.canRead(length)) {
            if(nextLine >= lines.size) {
                return false
            }
            setString(string.plus(lines[nextLine++]))
            if(nextLine < lines.size) {
                setString(string.plus('\n'))
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
        setString(string.substring(0, cursor))
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
        currentLine++
        return result
    }

    fun endStatement() {
        extendToLengthFromCursor(0)
        setString(string.substring(min(cursor, string.length)))
        readCharacters += cursor
        cursor = 0
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

    var currentIndentation: Int private set
    init { currentIndentation = 0 }

    fun readIndentation() {
        currentIndentation = 0
        while(canRead() && peek() == ' ') {
            skip()
            currentIndentation++
        }
    }

    inline fun trySkipWhitespace(reader: () -> Boolean): Boolean {
        val startCursor = cursor
        val startLine = currentLine
        skipWhitespace()
        if(!reader()) {
            cursor = startCursor
            currentLine = startLine
            return false
        }
        return true
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
            it.currentLine = currentLine
            it.scopeStack.addAll(scopeStack)
            it.updateLanguage()
            it.readCharacters = readCharacters
            it.currentIndentation = currentIndentation
            it.nextLine = nextLine
        }
    }

    fun copyFrom(other: DirectiveStringReader<*>) {
        cursor = other.cursor
        readCharacters = other.readCharacters
        setString(other.string)
        currentLine = other.currentLine
        nextLine = other.nextLine
    }

    fun skipTo(other: DirectiveStringReader<*>) {
        absoluteCursor = other.absoluteCursor
        extendToLengthFromCursor(0)
        currentLine = other.currentLine
    }

    override fun skipWhitespace() {
        while(canRead() && Character.isWhitespace(peek())) {
            if(read() == '\n') {
                currentLine++
            }
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

