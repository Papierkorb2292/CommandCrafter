package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.context.StringRange
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.NbtString
import net.minecraft.nbt.SnbtParsing
import net.minecraft.util.packrat.PackratParser
import net.minecraft.util.packrat.ParseErrorList
import net.minecraft.util.packrat.ReaderBackedParsingState
import net.minecraft.util.packrat.Symbol
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs
import net.papierkorb2292.command_crafter.editor.processing.helper.getSymbolByName
import net.papierkorb2292.command_crafter.helper.memoizeLast
import net.papierkorb2292.command_crafter.helper.runWithValue
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import org.eclipse.lsp4j.CompletionItemKind
import java.util.regex.Pattern

class NbtSuggestionResolver(private val stringReaderProvider: () -> StringReader, private val quoteRootStringPredicate: ((NbtString) -> Boolean)? = null) : StringRangeTree.SuggestionResolver<NbtElement> {
    private val SIMPLE_NAME = Pattern.compile("[A-Za-z0-9._+-]+")

    constructor(directiveReader: DirectiveStringReader<*>, quoteRootStringPredicate: ((NbtString) -> Boolean)? = null): this(directiveReader::copy, quoteRootStringPredicate)
    constructor(inputString: String, quoteRootStringPredicate: ((NbtString) -> Boolean)? = null): this({ StringReader(inputString) }, quoteRootStringPredicate)

    companion object {
        val keyParser: PackratParser<String>
        init {
            val snbtRules = SnbtParsing.createParser(NbtOps.INSTANCE).rules
            @Suppress("UNCHECKED_CAST")
            val mapKeySymbol = snbtRules.getSymbolByName("map_key") as Symbol<String>
            keyParser = PackratParser(snbtRules, snbtRules.get(mapKeySymbol));
        }
    }

    private val keyEndParser = { suggestionRange: StringRange ->
        val reader = stringReaderProvider()
        reader.cursor = suggestionRange.end
        reader.skipWhitespace()
        PackratParserAdditionalArgs.allowMalformed.runWithValue(true) {
            // Manually create parsing state so end cursor can be retrieved even when parsing wasn't successful
            val errorList = ParseErrorList.Impl<StringReader>()
            val parsingState = ReaderBackedParsingState(errorList, reader);
            val success = keyParser.startParsing(parsingState).isPresent
            if(!success)
                reader.cursor = errorList.cursor
        }
        if(reader.canRead() && reader.peek() == ':') {
            reader.skip()
            reader.skipWhitespace()
        }
        reader.cursor
    }.memoizeLast()

    override fun resolveNodeSuggestion(
        suggestion: StringRangeTree.Suggestion<NbtElement>,
        tree: StringRangeTree<NbtElement>,
        node: NbtElement,
        suggestionRange: StringRange,
        mappingInfo: FileMappingInfo,
        stringEscaper: StringRangeTree.StringEscaper,
    ): StringRangeTree.ResolvedSuggestion {
        val baseString =
            if(node != tree.root || suggestion.element !is NbtString || quoteRootStringPredicate?.invoke(suggestion.element) ?: true)
                suggestion.element.toString()
            else
                suggestion.element.value
        val elementString = stringEscaper.escape(baseString)
        val valueEnd = tree.ranges[node]!!.end
        return StringRangeTree.ResolvedSuggestion(
            valueEnd,
            SimpleCompletionItemProvider(elementString, suggestionRange.end, { valueEnd }, mappingInfo, kind=CompletionItemKind.Value)
        )
    }

    override fun resolveMapKeySuggestion(
        suggestion: StringRangeTree.Suggestion<NbtElement>,
        tree: StringRangeTree<NbtElement>,
        map: NbtElement,
        suggestionRange: StringRange,
        mappingInfo: FileMappingInfo,
        stringEscaper: StringRangeTree.StringEscaper,
    ): StringRangeTree.ResolvedSuggestion {
        val key = (suggestion.element as? NbtString)?.value ?: suggestion.element.toString()
        // Similar to StringNbtWriter.escapeName
        val escapedKey = if(SIMPLE_NAME.matcher(key).matches()) key else NbtString.escape(key)
        val keySuggestion = stringEscaper.escape("$escapedKey: ")
        val keyEnd = keyEndParser(suggestionRange)
        return StringRangeTree.ResolvedSuggestion(
            keyEnd,
            SimpleCompletionItemProvider(keySuggestion, suggestionRange.end, { keyEnd }, mappingInfo, key, CompletionItemKind.Property)
        )
    }
}