package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.context.StringRange
import net.minecraft.nbt.NbtElement
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.parser.FileMappingInfo

object NbtSuggestionResolver : StringRangeTree.SuggestionResolver<NbtElement> {
    override fun resolveSuggestion(
        suggestion: StringRangeTree.Suggestion<NbtElement>,
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
                    StringRangeTree.SimpleCompletionItemProvider(elementString, suggestionRange.end, { suggestionRange.end /*TODO*/ }, mappingInfo, languageServer)
                )
            }
            StringRangeTree.SuggestionType.MAP_KEY -> {
                val key = suggestion.element.asString()
                val keySuggestion = "\"$key\": "
                return StringRangeTree.ResolvedSuggestion(
                    StringRangeTree.SimpleInputMatcher(keySuggestion),
                    StringRangeTree.SimpleCompletionItemProvider(keySuggestion, suggestionRange.end, { suggestionRange.end /*TODO*/ }, mappingInfo, languageServer, key)
                )
            }
        }
    }
}