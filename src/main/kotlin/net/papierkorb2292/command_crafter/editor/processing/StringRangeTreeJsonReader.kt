package net.papierkorb2292.command_crafter.editor.processing

import com.google.gson.*
import com.google.gson.internal.LazilyParsedNumber
import com.mojang.brigadier.context.StringRange
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.processing.helper.createCursorMapperForEscapedCharacters
import net.papierkorb2292.command_crafter.helper.memoizeLast
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import net.papierkorb2292.command_crafter.parser.helper.SplitProcessedInputCursorMapper
import net.papierkorb2292.command_crafter.string_range_gson.JsonReader
import net.papierkorb2292.command_crafter.string_range_gson.JsonToken
import net.papierkorb2292.command_crafter.string_range_gson.Strictness
import org.eclipse.lsp4j.CompletionItemKind
import java.io.EOFException
import java.io.IOException
import java.io.Reader
import java.io.StringReader
import java.util.*
import kotlin.math.max

class StringRangeTreeJsonReader(private val jsonReaderProvider: () -> JsonReader) {

    constructor(stringReader: Reader): this({ JsonReader(stringReader) })

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
        val `in` = jsonReaderProvider()
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
                    try {
                        if(!`in`.hasNext()) {
                            if(current is JsonArray && current.size() == 0 || current is JsonObject && current.size() == 0) {
                                builder.addRangeBetweenInternalNodeEntries(current, StringRange(`in`.absoluteEntryEndPos, max(`in`.absoluteValueStartPos, `in`.absoluteEntryEndPos)))
                            }
                            break
                        }
                    } catch(e: Throwable) {
                        if(!allowMalformed || e !is IOException && e !is IllegalStateException) {
                            throw e
                        }
                        `in`.pos = max(`in`.pos - 1, 0) // There probably was a nextNonWhitespace call, which could've skipped ',' or ';' or '}'
                        val entryEnd = `in`.absoluteEntryEndPos
                        `in`.skipEntry()
                        if(`in`.peek() == JsonToken.END_DOCUMENT)
                            continue
                        val rangeEnd =
                            if(`in`.peek() == JsonToken.END_ARRAY || `in`.peek() == JsonToken.END_OBJECT) `in`.absolutePos - 1
                            else `in`.absolutePos
                        builder.addRangeBetweenInternalNodeEntries(current, StringRange(entryEnd, rangeEnd))
                        continue
                    }
                    builder.addRangeBetweenInternalNodeEntries(current, StringRange(`in`.absoluteEntryEndPos, `in`.absoluteValueStartPos))

                    var name: String? = null
                    // Name is only used for JSON object members
                    if(current is JsonObject) {
                        name = `in`.nextName()
                        builder.addMapKeyRange(current, JsonPrimitive(name), StringRange(`in`.absoluteValueStartPos, `in`.absolutePos))
                    }

                    var isNesting: Boolean
                    var value: JsonElement?

                    var valueStartPos: Int
                    var valueEndPos: Int
                    try {
                        peeked = `in`.peek()
                        value = tryBeginNesting(`in`, peeked)
                        isNesting = value != null

                        if(value == null) {
                            value = readTerminal(`in`, peeked)
                        }

                        valueStartPos = `in`.absoluteValueStartPos
                        valueEndPos = `in`.absolutePos
                    } catch(e: Throwable) {
                        if(!allowMalformed || e !is IOException && e !is IllegalStateException) {
                            throw e
                        }
                        @Suppress("DEPRECATION")
                        value = JsonNull()
                        builder.addPlaceholderNode(value)
                        isNesting = false
                        `in`.pos = max(`in`.pos - 1, 0) // There probably was a nextNonWhitespace call, which could've skipped ',' or ';' or '}' or ']'
                        valueStartPos = `in`.absolutePos
                        `in`.skipEntry()
                        if(`in`.absolutePos > valueStartPos)
                            valueStartPos = `in`.absolutePos - 1
                        valueEndPos = valueStartPos
                    }

                    if(current is JsonArray) {
                        current.add(value)
                    } else {
                        (current as JsonObject).add(name, value)
                    }

                    if(isNesting) {
                        stack.addLast(ReaderStackEntry(current, nestedStartPos, nestedAllowedStartPos))
                        nestedStartPos = `in`.absoluteValueStartPos
                        nestedAllowedStartPos = `in`.absoluteValueStartPosBeforeWhitespace
                        current = value!!
                        builder.addNodeOrder(current)
                    } else {
                        // There could have been an error thrown between assigning the two, which would cause the latter to be ahead of the former
                        builder.addNode(value!!, StringRange(valueStartPos, valueEndPos), `in`.absoluteValueStartPosBeforeWhitespace)
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
        } catch(e: Throwable) {
            if(!allowMalformed || e !is IOException && e !is IllegalStateException) {
                throw e
            }
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

    class StringRangeTreeSuggestionResolver(private val readerProvider: () -> Reader) : StringRangeTree.SuggestionResolver<JsonElement> {

        constructor(directiveReader: DirectiveStringReader<*>): this({ directiveReader.copy().asReader() })
        constructor(inputString: String): this({ StringReader(inputString) })

        private val valueEndParser = { suggestionRange: StringRange ->
            val inputReader = readerProvider()
            inputReader.skip(suggestionRange.end.toLong())
            val jsonReader = JsonReader(inputReader)
            suggestionRange.end + try {
                val childStringRangeTree = StringRangeTreeJsonReader { jsonReader }.read(Strictness.LENIENT, true)
                childStringRangeTree.ranges[childStringRangeTree.root]!!.end
            } catch(e: EOFException) {
                jsonReader.absolutePos
            } catch(e: Exception) {
                max(jsonReader.absolutePos - 1, 0)
            }
        }.memoizeLast()

        private val keyEndParser = { suggestionRange: StringRange ->
            val inputReader = readerProvider()
            inputReader.skip(suggestionRange.end.toLong())
            val jsonReader = JsonReader(inputReader)
            jsonReader.strictness = Strictness.LENIENT
            jsonReader.stack[0] = 3 // EMPTY_OBJECT
            try {
                jsonReader.nextName()
                try {
                    // Skip ':'
                    jsonReader.nextNonWhitespace(true)
                    // Skip whitespace after ':'
                    jsonReader.nextNonWhitespace(true)
                    jsonReader.pos--
                } catch(ignored: Exception) { }
            } catch(e: Exception) {
                jsonReader.pos--
            }
            suggestionRange.end + max(jsonReader.absolutePos, 0)
        }.memoizeLast()

        override fun resolveNodeSuggestion(
            suggestion: StringRangeTree.Suggestion<JsonElement>,
            tree: StringRangeTree<JsonElement>,
            node: JsonElement,
            suggestionRange: StringRange,
            mappingInfo: FileMappingInfo,
            stringEscaper: StringRangeTree.StringEscaper
        ): StringRangeTree.ResolvedSuggestion {
            val elementString = stringEscaper.escape(suggestion.element.toString())
            val replaceEnd = valueEndParser(suggestionRange)
            return StringRangeTree.ResolvedSuggestion(
                replaceEnd,
                SimpleCompletionItemProvider(elementString, suggestionRange.end, { replaceEnd }, mappingInfo, kind = CompletionItemKind.Value)
            )
        }

        override fun resolveMapKeySuggestion(
            suggestion: StringRangeTree.Suggestion<JsonElement>,
            tree: StringRangeTree<JsonElement>,
            map: JsonElement,
            suggestionRange: StringRange,
            mappingInfo: FileMappingInfo,
            stringEscaper: StringRangeTree.StringEscaper
        ): StringRangeTree.ResolvedSuggestion {
            val element = suggestion.element
            val key = if(element.isJsonPrimitive) element.asString else element.toString()
            val keySuggestion = stringEscaper.escape("\"$key\": ")
            val replaceEnd = keyEndParser(suggestionRange)
            return StringRangeTree.ResolvedSuggestion(
                replaceEnd,
                SimpleCompletionItemProvider(keySuggestion, suggestionRange.end, { replaceEnd }, mappingInfo, key, CompletionItemKind.Property)
            )
        }
    }

    class StringContentGetter(val tree: StringRangeTree<JsonElement>, val input: String): (JsonElement) -> Triple<String, SplitProcessedInputCursorMapper, StringRangeTree.StringEscaper>? {
        override fun invoke(p1: JsonElement): Triple<String, SplitProcessedInputCursorMapper, StringRangeTree.StringEscaper>? {
            if(p1 !is JsonPrimitive || !p1.isString)
                return null
            val range = tree.ranges[p1]!!
            val firstChar = input[range.start]
            val isQuoted = firstChar == '"' || firstChar == '\''
            val sourceString =
                if(isQuoted)
                    // If the string is missing content and end quotes, end-1 will be before start+1
                    if(range.end - 1 > range.start)
                        input.substring(range.start + 1, range.end - 1)
                    else
                        input.substring(range.start + 1, range.end)
                else
                    input.substring(range.start, range.end)
            return Triple(
                p1.asString,
                createCursorMapperForEscapedCharacters(sourceString, range.start + 1),
                if(isQuoted) StringRangeTree.StringEscaper.escapeForQuotes(firstChar.toString()) else StringRangeTree.StringEscaper.Identity
            )
        }
    }
}