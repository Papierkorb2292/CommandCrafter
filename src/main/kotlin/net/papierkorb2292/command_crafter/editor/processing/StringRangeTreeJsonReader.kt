package net.papierkorb2292.command_crafter.editor.processing

import com.google.gson.*
import com.google.gson.internal.LazilyParsedNumber
import com.mojang.brigadier.context.StringRange
import com.mojang.datafixers.util.Pair
import net.papierkorb2292.command_crafter.string_range_gson.JsonReader
import net.papierkorb2292.command_crafter.string_range_gson.JsonToken
import net.papierkorb2292.command_crafter.string_range_gson.Strictness
import java.io.IOException
import java.io.Reader
import java.util.*

class StringRangeTreeJsonReader(private val stringReader: Reader) {
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
                @Suppress("DEPRECATION")
                JsonNull() // Not using JsonNull.INSTANCE, because StringRangeTree requires each node to be a unique instance
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
        builder.addNode(terminal, StringRange(startPos, `in`.absolutePos))
        return builder.build(terminal)
    }

    fun read(): StringRangeTree<JsonElement> {
        val `in` = JsonReader(stringReader)
        `in`.strictness = Strictness.LENIENT
        val builder = StringRangeTree.Builder<JsonElement>()


        var current: JsonElement
        val startAbsolutePos = `in`.absolutePos
        var peeked = `in`.peek()

        var nestedStartPos = startAbsolutePos

        current = tryBeginNesting(`in`, peeked)
            ?: return readOnlyTerminal(`in`, peeked, startAbsolutePos, builder)

        builder.addNodeOrder(current)

        val stack: Deque<ReaderStackEntry> = ArrayDeque()

        try {
            while(true) {
                while(`in`.hasNext()) {
                    if(!`in`.hasNext()) break

                    if(`in`.absoluteEntryEndPos != -1) {
                        builder.addRangeBetweenInternalNodeEntries(current, StringRange(`in`.absoluteEntryEndPos, `in`.absoluteValueStartPos))
                    }

                    var name: String? = null
                    // Name is only used for JSON object members
                    if(current is JsonObject) {
                        name = `in`.nextName()
                        builder.addMapKeyRange(current, StringRange(`in`.absoluteValueStartPos, `in`.absolutePos))
                    }

                    peeked = `in`.peek()
                    var value = tryBeginNesting(`in`, peeked)
                    val isNesting = value != null

                    if(value == null) {
                        value = readTerminal(`in`, peeked)
                    }

                    if(current is JsonArray) {
                        current.add(value)
                    } else {
                        (current as JsonObject).add(name, value)
                    }

                    // Range isn't accurate for nested elements, but it's replaced later.
                    // It is important to add it here to keep the correct order.

                    if(isNesting) {
                        stack.addLast(ReaderStackEntry(current, nestedStartPos))
                        nestedStartPos = `in`.absoluteValueStartPos
                        current = value
                        builder.addNodeOrder(current)
                    } else {
                        builder.addNode(value, StringRange(`in`.absoluteValueStartPos, `in`.absolutePos))
                    }
                }

                // End current element
                if(current is JsonArray) {
                    `in`.endArray()
                } else {
                    `in`.endObject()
                }
                builder.addNode(current, StringRange(nestedStartPos, `in`.absolutePos))

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
        } catch(e: IOException) {
            return builder.build(stack.peekLast()?.element ?: current)
        }
    }

    private data class ReaderStackEntry(val element: JsonElement, val startPos: Int)

    object StringRangeTreeSemanticTokenProvider : StringRangeTree.SemanticTokenProvider<JsonElement> {
        override fun getMapNameTokenInfo(map: JsonElement) =
            StringRangeTree.TokenInfo(TokenType.PARAMETER, 1)

        override fun getNodeTokenInfo(node: JsonElement) = when(node) {
            is JsonPrimitive -> {
                if(node.isBoolean) StringRangeTree.TokenInfo(TokenType.ENUM_MEMBER, 0)
                else if(node.isNumber) StringRangeTree.TokenInfo(TokenType.NUMBER, 0)
                else if(node.isString) StringRangeTree.TokenInfo(TokenType.STRING, 0)
                else throw IllegalArgumentException("Unexpected JsonPrimitive type: $node")
            }
            is JsonNull -> StringRangeTree.TokenInfo(TokenType.KEYWORD, 0)
            else -> null
        }

        override fun getAdditionalTokens(node: JsonElement) = emptyList<StringRangeTree.AdditionalToken>()
    }
}