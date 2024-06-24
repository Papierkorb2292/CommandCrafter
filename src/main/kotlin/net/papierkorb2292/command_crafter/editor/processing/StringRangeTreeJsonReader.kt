package net.papierkorb2292.command_crafter.editor.processing

import com.google.gson.*
import com.google.gson.internal.LazilyParsedNumber
import com.mojang.brigadier.context.StringRange
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import net.papierkorb2292.command_crafter.string_range_gson.JsonReader
import net.papierkorb2292.command_crafter.string_range_gson.JsonToken
import net.papierkorb2292.command_crafter.string_range_gson.MalformedJsonException
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
        builder.addNode(terminal, StringRange(startPos, `in`.absolutePos), 0)
        return builder.build(terminal)
    }

    fun read(strictness: Strictness = Strictness.STRICT, allowMalformed: Boolean = false): StringRangeTree<JsonElement> {
        val `in` = JsonReader(stringReader)
        `in`.strictness = strictness
        val builder = StringRangeTree.Builder<JsonElement>()

        var current: JsonElement
        val startAbsolutePos = `in`.absolutePos
        var peeked = `in`.peek()

        var nestedStartPos = startAbsolutePos
        var nestedAllowedStartPos = 0

        current = tryBeginNesting(`in`, peeked)
            ?: return readOnlyTerminal(`in`, peeked, startAbsolutePos, builder)

        builder.addNodeOrder(current)

        val stack: Deque<ReaderStackEntry> = ArrayDeque()

        try {
            while(true) {
                while(true) {
                    val hasNext = try {
                        `in`.hasNext()
                    } catch(e: MalformedJsonException) {
                        if(!allowMalformed) {
                            throw e
                        }
                        `in`.skipEntry()
                        continue
                    }
                    if(!hasNext) {
                        builder.addRangeBetweenInternalNodeEntries(current, StringRange(`in`.absoluteEntryEndPos, `in`.absolutePos - 1))
                        break
                    }
                    if(`in`.absoluteEntryEndPos != -1) {
                        builder.addRangeBetweenInternalNodeEntries(current, StringRange(`in`.absoluteEntryEndPos, `in`.absoluteValueStartPos))
                    }

                    var name: String? = null
                    // Name is only used for JSON object members
                    if(current is JsonObject) {
                        name = `in`.nextName()
                        builder.addMapKeyRange(current, StringRange(`in`.absoluteValueStartPos, `in`.absolutePos))
                    }

                    val isNesting: Boolean
                    var value: JsonElement?

                    try {
                        peeked = `in`.peek()
                        value = tryBeginNesting(`in`, peeked)
                        isNesting = value != null

                        if(value == null) {
                            value = readTerminal(`in`, peeked)
                        }
                    } catch(e: MalformedJsonException) {
                        if(!allowMalformed) {
                            throw e
                        }
                        if(current is JsonObject) {
                            @Suppress("DEPRECATION")
                            current.add(name, JsonNull())
                        }
                        `in`.skipEntry()
                        continue
                    }

                    if(current is JsonArray) {
                        current.add(value)
                    } else {
                        (current as JsonObject).add(name, value)
                    }

                    // Range isn't accurate for nested elements, but it's replaced later.
                    // It is important to add it here to keep the correct order.

                    if(isNesting) {
                        stack.addLast(ReaderStackEntry(current, nestedStartPos, nestedAllowedStartPos))
                        nestedStartPos = `in`.absoluteValueStartPos
                        nestedAllowedStartPos = `in`.absoluteValueStartPosBeforeWhitespace
                        current = value
                        builder.addNodeOrder(current)
                    } else {
                        builder.addNode(value, StringRange(`in`.absoluteValueStartPos, `in`.absolutePos), `in`.absoluteValueStartPosBeforeWhitespace)
                    }
                }

                // End current element
                if(current is JsonArray) {
                    `in`.endArray()
                } else {
                    `in`.endObject()
                }
                builder.addNode(current, StringRange(nestedStartPos, `in`.absolutePos), nestedAllowedStartPos)

                if(stack.isEmpty()) {
                    return builder.build(current)
                } else {
                    // Continue with enclosing element
                    stack.removeLast().apply {
                        current = element
                        nestedStartPos = startPos
                        nestedAllowedStartPos = allowedStartPos
                    }
                }
            }
        } catch(e: IOException) {
            for(entry in stack)
                builder.addNode(entry.element, StringRange(entry.startPos, `in`.absolutePos), entry.allowedStartPos)
            builder.addNode(current, StringRange(nestedStartPos, `in`.absolutePos), nestedAllowedStartPos)
            return builder.build(stack.peekLast()?.element ?: current)
        }
    }

    private data class ReaderStackEntry(val element: JsonElement, val startPos: Int, val allowedStartPos: Int)

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

    object StringRangeTreeSuggestionResolver : StringRangeTree.SuggestionResolver<JsonElement> {
        override fun resolveSuggestion(
            suggestion: StringRangeTree.Suggestion<JsonElement>,
            suggestionType: StringRangeTree.SuggestionType,
            languageServer: MinecraftLanguageServer,
            suggestionRange: StringRange,
            mappingInfo: FileMappingInfo,
        ): StringRangeTree.ResolvedSuggestion {
            when(suggestionType) {
                StringRangeTree.SuggestionType.NODE_START -> {
                    val elementString = suggestion.element.toString()
                    return StringRangeTree.ResolvedSuggestion(
                        StringRangeTree.SimpleInputMatcher(elementString),
                        StringRangeTree.SimpleCompletionItemProvider(elementString, suggestionRange, mappingInfo)
                    )
                }
                StringRangeTree.SuggestionType.MAP_KEY -> {
                    val element = suggestion.element
                    val key = if(element.isJsonPrimitive) element.asString else element.toString()
                    val keySuggestion = "\"$key\": "
                    return StringRangeTree.ResolvedSuggestion(
                        StringRangeTree.SimpleInputMatcher(keySuggestion),
                        StringRangeTree.SimpleCompletionItemProvider(keySuggestion, suggestionRange, mappingInfo, key)
                    )
                }
            }
        }
    }
}