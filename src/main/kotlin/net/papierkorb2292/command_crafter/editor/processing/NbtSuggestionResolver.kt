package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.context.StringRange
import net.minecraft.nbt.*
import net.minecraft.util.parsing.packrat.Atom
import net.minecraft.util.parsing.packrat.ErrorCollector
import net.minecraft.util.parsing.packrat.commands.Grammar
import net.minecraft.util.parsing.packrat.commands.StringReaderParserState
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs
import net.papierkorb2292.command_crafter.editor.processing.helper.getSymbolByName
import net.papierkorb2292.command_crafter.helper.memoizeLast
import net.papierkorb2292.command_crafter.helper.runWithValue
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.CompletionItemKind
import java.util.regex.Pattern

class NbtSuggestionResolver(private val stringReaderProvider: () -> StringReader, private val quoteRootStringPredicate: ((StringTag) -> Boolean)? = null) : StringRangeTree.SuggestionResolver<Tag> {
    private val SIMPLE_NAME = Pattern.compile("[A-Za-z0-9._+-]+")

    constructor(directiveReader: DirectiveStringReader<*>, quoteRootStringPredicate: ((StringTag) -> Boolean)? = null): this(directiveReader::copy, quoteRootStringPredicate)
    constructor(inputString: String, quoteRootStringPredicate: ((StringTag) -> Boolean)? = null): this({ StringReader(inputString) }, quoteRootStringPredicate)

    companion object {
        val keyParser: Grammar<String>
        init {
            val snbtRules = SnbtGrammar.createParser(NbtOps.INSTANCE).rules
            @Suppress("UNCHECKED_CAST")
            val mapKeySymbol = snbtRules.getSymbolByName("map_key") as Atom<String>
            keyParser = Grammar(snbtRules, snbtRules.getOrThrow(mapKeySymbol));
        }
    }

    private val keyEndParser = { suggestionRange: StringRange ->
        val reader = stringReaderProvider()
        reader.cursor = suggestionRange.end
        reader.skipWhitespace()
        PackratParserAdditionalArgs.allowMalformed.runWithValue(true) {
            // Manually create parsing state so end cursor can be retrieved even when parsing wasn't successful
            val errorList = ErrorCollector.LongestOnly<StringReader>()
            val parsingState = StringReaderParserState(errorList, reader);
            val success = keyParser.parse(parsingState).isPresent
            if(!success)
                reader.cursor = errorList.cursor()
        }
        if(reader.canRead() && reader.peek() == ':') {
            reader.skip()
            reader.skipWhitespace()
        }
        reader.cursor
    }.memoizeLast()

    override fun resolveNodeSuggestion(
        suggestionProviders: Collection<StringRangeTree.SuggestionProvider<Tag>>,
        tree: StringRangeTree<Tag>,
        node: Tag,
        suggestionRange: StringRange,
        mappingInfo: FileMappingInfo,
        stringEscaper: StringRangeTree.StringEscaper,
    ): StringRangeTree.ResolvedSuggestion {
        val valueEnd = tree.ranges[node]!!.end
        return StringRangeTree.ResolvedSuggestion(
            valueEnd,
            StreamCompletionItemProvider(suggestionRange.end, { valueEnd }, mappingInfo, CompletionItemKind.Value) {
                suggestionProviders.stream().flatMap { it.createSuggestions() }.distinct().map { suggestion ->
                    val baseString =
                        if(suggestion.isNumberABoolean) {
                            suggestion.element.asBoolean().orElseThrow {
                                IllegalArgumentException("Boolean suggestion didn't represent a boolean: $suggestion")
                            }.toString()
                        } else if(node != tree.root || suggestion.element !is StringTag || quoteRootStringPredicate?.invoke(suggestion.element) ?: true)
                            suggestion.element.toString()
                        else
                            suggestion.element.value
                    StreamCompletionItemProvider.Completion(stringEscaper.escape(baseString), completionModifier = { completion ->
                        if(suggestion.element is CompoundTag && suggestion.element.isEmpty || suggestion.element is CollectionTag && suggestion.element.isEmpty) {
                            // Must be done with a command instead of additionalTextEdit, because the latter would cause problems when the cursor is at the end of a line
                            // (and additionalTextEdit isn't meant to be used for completions at the cursor position)
                            completion.command = Command("Move cursor into node", "cursorLeft")
                        }
                        suggestion.completionModifier?.invoke(completion)
                    })
                }
            }
        )
    }

    override fun resolveMapKeySuggestion(
        suggestionProviders: Collection<StringRangeTree.SuggestionProvider<Tag>>,
        tree: StringRangeTree<Tag>,
        map: Tag,
        suggestionRange: StringRange,
        mappingInfo: FileMappingInfo,
        stringEscaper: StringRangeTree.StringEscaper,
    ): StringRangeTree.ResolvedSuggestion {
        // Clear args, because keyEndParser could mess with them otherwise, leading to problems when invoking the NbtSuggestionResolver as part of a packrat parser (like in item predicates)
        val restoreArgsCallback = PackratParserAdditionalArgs.temporarilyClearArgs()
        val keyEnd = try {
            keyEndParser(suggestionRange)
        } finally {
            restoreArgsCallback()
        }
        return StringRangeTree.ResolvedSuggestion(
            keyEnd,
            StreamCompletionItemProvider(suggestionRange.end, { keyEnd }, mappingInfo, CompletionItemKind.Property) {
                suggestionProviders.stream().flatMap { it.createSuggestions() }.distinct().map { suggestion ->
                    val key = (suggestion.element as? StringTag)?.value ?: suggestion.element.toString()
                    // Similar to StringNbtWriter.escapeName
                    val escapedKey = if(SIMPLE_NAME.matcher(key).matches()) key else StringTag.quoteAndEscape(key)
                    StreamCompletionItemProvider.Completion(stringEscaper.escape("$escapedKey: "), key, suggestion.completionModifier)
                }
            }
        )
    }
}