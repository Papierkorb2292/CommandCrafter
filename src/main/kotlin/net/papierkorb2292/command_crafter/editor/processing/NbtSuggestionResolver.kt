package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtString
import net.minecraft.nbt.StringNbtReader
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.processing.helper.AllowMalformedContainer
import net.papierkorb2292.command_crafter.helper.memoizeLast
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import org.eclipse.lsp4j.CompletionItemKind
import java.util.regex.Pattern

class NbtSuggestionResolver(private val stringReaderProvider: () -> StringReader) : StringRangeTree.SuggestionResolver<NbtElement> {
    private val SIMPLE_NAME = Pattern.compile("[A-Za-z0-9._+-]+")

    constructor(directiveReader: DirectiveStringReader<*>): this(directiveReader::copy)
    constructor(inputString: String): this({ StringReader(inputString) })

    private val valueEndParser = { suggestionRange: StringRange ->
        val reader = stringReaderProvider()
        reader.cursor = suggestionRange.end
        reader.skipWhitespace()
        val nbtReader = StringNbtReader(reader)
        @Suppress("KotlinConstantConditions")
        (nbtReader as AllowMalformedContainer).`command_crafter$setAllowMalformed`(true)
        try {
            nbtReader.parseElement()
        } catch(ignored: CommandSyntaxException) { }
        reader.cursor
    }.memoizeLast()

    private val keyEndParser = { suggestionRange: StringRange ->
        val reader = stringReaderProvider()
        reader.cursor = suggestionRange.end
        reader.skipWhitespace()
        reader.readString()
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
        languageServer: MinecraftLanguageServer,
        suggestionRange: StringRange,
        mappingInfo: FileMappingInfo,
        stringEscaper: StringRangeTree.StringEscaper
    ): StringRangeTree.ResolvedSuggestion {
        val elementString = stringEscaper.escape(suggestion.element.toString())
        val valueEnd = valueEndParser(suggestionRange)
        return StringRangeTree.ResolvedSuggestion(
            valueEnd,
            SimpleCompletionItemProvider(elementString, suggestionRange.end, { valueEnd }, mappingInfo, languageServer, kind=CompletionItemKind.Value)
        )
    }

    override fun resolveMapKeySuggestion(
        suggestion: StringRangeTree.Suggestion<NbtElement>,
        tree: StringRangeTree<NbtElement>,
        map: NbtElement,
        languageServer: MinecraftLanguageServer,
        suggestionRange: StringRange,
        mappingInfo: FileMappingInfo,
        stringEscaper: StringRangeTree.StringEscaper
    ): StringRangeTree.ResolvedSuggestion {
        val key = suggestion.element.asString()
        // Similar to StringNbtWriter.escapeName
        val escapedKey = if(SIMPLE_NAME.matcher(key).matches()) key else NbtString.escape(key)
        val keySuggestion = stringEscaper.escape("$escapedKey: ")
        val keyEnd = keyEndParser(suggestionRange)
        return StringRangeTree.ResolvedSuggestion(
            keyEnd,
            SimpleCompletionItemProvider(keySuggestion, suggestionRange.end, { keyEnd }, mappingInfo, languageServer, key, CompletionItemKind.Property)
        )
    }
}