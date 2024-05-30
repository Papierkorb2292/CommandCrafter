package net.papierkorb2292.command_crafter.editor.processing

import com.google.gson.*
import com.google.gson.internal.LazilyParsedNumber
import com.google.gson.internal.bind.TypeAdapters
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import com.mojang.brigadier.context.StringRange
import java.io.IOException
import java.io.Reader
import java.io.StringReader
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.*

class StringRangeTreeTypeAdapter(val reader: StringRangeTreeStringReader) : TypeAdapter<StringRangeTree<JsonElement>>() {
    companion object {
        // Perfectly clean code ;)
        private val jsonReaderPos: Field = JsonReader::class.java.getDeclaredField("pos")
            .apply { isAccessible = true }
        private var JsonReader.pos: Int
            get() = jsonReaderPos.getInt(this)
            set(value) { jsonReaderPos.setInt(this, value) }

        private val jsonReaderLimit: Field = JsonReader::class.java.getDeclaredField("limit")
            .apply { isAccessible = true }
        private val JsonReader.limit: Int
            get() = jsonReaderLimit.getInt(this)

        private val jsonReaderStack: Field = JsonReader::class.java.getDeclaredField("stack")
            .apply { isAccessible = true }
        private val JsonReader.stack: IntArray
            get() = jsonReaderStack.get(this) as IntArray

        private val jsonReaderStackSize: Field = JsonReader::class.java.getDeclaredField("stackSize")
            .apply { isAccessible = true }
        private val JsonReader.stackSize: Int
            get() = jsonReaderStackSize.getInt(this)

        private var JsonReader.stackTop
            get() = stack[stackSize - 1]
            set(value) { stack[stackSize - 1] = value }

        private val jsonReaderNextNonWhitespace: Method = JsonReader::class.java.getDeclaredMethod("nextNonWhitespace", Boolean::class.java)
            .apply { isAccessible = true }
        private fun JsonReader.nextNonWhitespace(throwOnEof: Boolean) =
            jsonReaderNextNonWhitespace.invoke(this, throwOnEof) as Int

        private val jsonReaderFillBuffer: Method = JsonReader::class.java.getDeclaredMethod("fillBuffer", Boolean::class.java)
            .apply { isAccessible = true }
        private fun JsonReader.fillBuffer(minimum: Int) =
            jsonReaderFillBuffer.invoke(this, minimum) as Boolean

        private val jsonReaderBuffer: Field = JsonReader::class.java.getDeclaredField("buffer")
            .apply { isAccessible = true }
        private val JsonReader.buffer: CharArray
            get() = jsonReaderBuffer.get(this) as CharArray

        private val jsonReaderSyntaxError: Method = JsonReader::class.java.getDeclaredMethod("syntaxError", String::class.java)
            .apply { isAccessible = true }
        private fun JsonReader.syntaxError(message: String) =
            jsonReaderSyntaxError.invoke(this, message) as IOException

        private val jsonReaderCheckLenient: Method = JsonReader::class.java.getDeclaredMethod("checkLenient")
            .apply { isAccessible = true }
        private fun JsonReader.checkLenient() =
            jsonReaderCheckLenient.invoke(this)
    }

    private fun getAbsolutePos(`in`: JsonReader): Int {
        return reader.consumedChars - `in`.limit + `in`.pos
    }

    override fun write(out: JsonWriter, value: StringRangeTree<JsonElement>) {
        TypeAdapters.JSON_ELEMENT.write(out, value.root)
    }

    @Throws(IOException::class)
    private fun tryBeginNesting(`in`: JsonReader, peeked: JsonToken): JsonElement? {
        return when(peeked) {
            JsonToken.BEGIN_ARRAY -> {
                `in`.beginArray()
                JsonArray()
            }

            JsonToken.BEGIN_OBJECT -> {
                `in`.beginObject()
                JsonObject()
            }

            else -> null
        }
    }

    /** Reads a [JsonElement] which cannot have any nested elements  */
    @Throws(IOException::class)
    private fun readTerminal(`in`: JsonReader, peeked: JsonToken): JsonElement {
        return when(peeked) {
            JsonToken.STRING -> JsonPrimitive(`in`.nextString())
            JsonToken.NUMBER -> {
                val number = `in`.nextString()
                JsonPrimitive(LazilyParsedNumber(number))
            }

            JsonToken.BOOLEAN -> JsonPrimitive(`in`.nextBoolean())
            JsonToken.NULL -> {
                `in`.nextNull()
                JsonNull.INSTANCE
            }

            // When read(JsonReader) is called with JsonReader in invalid state
            else -> throw IllegalStateException("Unexpected token: $peeked")
        }
    }

    /** Reads a [JsonElement] which cannot have any nested elements and that is the only
     * node in the input reader such that this method can immediately build a StringRangeTree
     * for it.*/
    @Throws(IOException::class)
    private fun readOnlyTerminal(`in`: JsonReader, peeked: JsonToken, startPos: Int, builder: StringRangeTree.Builder<JsonElement>): StringRangeTree<JsonElement> {
        val terminal = readTerminal(`in`, peeked)
        builder.addNode(terminal, StringRange(startPos, reader.consumedChars))
        return builder.build(terminal)
    }

    @Throws(IOException::class)
    override fun read(`in`: JsonReader): StringRangeTree<JsonElement> {
        val builder = StringRangeTree.Builder<JsonElement>()
        val isLenient = `in`.isLenient
        // Either JsonArray or JsonObject
        var currentPos = getAbsolutePos(`in`)
        var current: JsonElement
        var peeked = `in`.peek()

        var nestedStartPos = currentPos

        current = tryBeginNesting(`in`, peeked)
            ?: return readOnlyTerminal(`in`, peeked, currentPos, builder)

        currentPos = getAbsolutePos(`in`)

        val stack: Deque<ReaderStackEntry> = ArrayDeque()

        while(true) {
            while(true) {
                if(current is JsonObject) {
                    //Read range before next name
                    val betweenEntryRangeStart = currentPos
                    `in`.nextNonWhitespace(false)
                    `in`.pos--
                    currentPos = getAbsolutePos(`in`)
                    builder.addRangeBetweenMapEntries(current, StringRange(betweenEntryRangeStart, currentPos))
                }

                if(!`in`.hasNext()) break

                if(current is JsonArray && current.size() > 0) {
                    currentPos++ //Skip comma
                }

                var name: String? = null
                // Name is only used for JSON object members
                if(current is JsonObject) {
                    name = `in`.nextName()

                    // Replicates logic from JsonReader.doPeek, to get the correct start position of the value that is read (after ':')

                    // Look for a colon before the value.
                    val c = `in`.nextNonWhitespace(true).toChar()
                    when(c) {
                        ':' -> {}
                        '=' -> {
                            `in`.checkLenient()
                            if((`in`.pos < `in`.limit || `in`.fillBuffer(1)) && `in`.buffer[`in`.pos] == '>') {
                                `in`.pos++
                            }
                        }

                        else -> throw `in`.syntaxError("Expected ':'")
                    }

                    currentPos = getAbsolutePos(`in`)

                    `in`.stackTop = 6 //JsonScope.EMPTY_DOCUMENT, this makes doPeek directly read a JSON value (if it isn't in lenient mode)
                    `in`.isLenient = false
                    peeked = `in`.peek()
                    `in`.isLenient = isLenient
                    `in`.stackTop = 5 //JsonScope.NONEMPTY_OBJECT, this is what the stack would usually contain after reading a name and value
                } else {
                    peeked = `in`.peek()
                }

                val valueStartPos = currentPos

                var value = tryBeginNesting(`in`, peeked)
                val isNesting = value != null

                if(value == null) {
                    value = readTerminal(`in`, peeked)
                }

                currentPos = getAbsolutePos(`in`)

                if(current is JsonArray) {
                    current.add(value)
                } else {
                    (current as JsonObject).add(name, value)
                }

                if(isNesting) {
                    stack.addLast(ReaderStackEntry(current, nestedStartPos))
                    nestedStartPos = valueStartPos
                    current = value
                } else {
                    builder.addNode(value, StringRange(valueStartPos, currentPos))
                }
            }

            // End current element
            if(current is JsonArray) {
                `in`.endArray()
            } else {
                `in`.endObject()
            }
            currentPos = getAbsolutePos(`in`)
            builder.addRangeBetweenMapEntries(current, StringRange(nestedStartPos, currentPos))

            if(stack.isEmpty()) {
                return builder.build(current)
            } else {
                // Continue with enclosing element
                stack.removeLast().apply {
                    current = element
                    nestedStartPos = startPos
                }
            }
        }
    }

    class StringRangeTreeStringReader(private val delegateReader: Reader) : Reader() {
        constructor(input: String): this(StringReader(input))

        var consumedChars = 0

        private var consumedCharsMark = 0

        override fun read(): Int {
            consumedChars++
            return delegateReader.read()
        }
        override fun read(cbuf: CharArray, off: Int, len: Int): Int {
            consumedChars += len
            return delegateReader.read(cbuf, off, len)
        }
        override fun skip(n: Long): Long {
            consumedChars += n.toInt()
            return delegateReader.skip(n)
        }
        override fun ready() = delegateReader.ready()
        override fun markSupported() = delegateReader.markSupported()
        override fun mark(readAheadLimit: Int) {
            consumedCharsMark = consumedChars
            delegateReader.mark(readAheadLimit)
        }
        override fun reset() {
            consumedChars = consumedCharsMark
            delegateReader.reset()
        }
        override fun close() = delegateReader.close()
    }

    private data class ReaderStackEntry(val element: JsonElement, val startPos: Int)

    object StringRangeTreeSemanticTokenProvider : StringRangeTree.SemanticTokenProvider<JsonElement> {
        override fun getTokenType(node: JsonElement): TokenType? {
            when(node) {
                is JsonPrimitive -> {
                    if(node.isBoolean) return TokenType.ENUM_MEMBER
                    if(node.isNumber) return TokenType.NUMBER
                    if(node.isString) return TokenType.STRING
                }
                is JsonNull -> return TokenType.KEYWORD
                else -> return null
            }
        }

        override fun getModifiers(node: JsonElement) = 0
    }
}