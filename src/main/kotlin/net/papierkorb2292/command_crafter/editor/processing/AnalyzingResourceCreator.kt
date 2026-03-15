package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.core.RegistryAccess
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import java.util.*

class AnalyzingResourceCreator(val languageServer: MinecraftLanguageServer?, val sourceFunctionUri: String, val registries: RegistryAccess) {
    val resourceStack: Deque<ResourceStackEntry> = LinkedList()

    /**
     * If not null, the analyzing is done only to request suggestions at one specific position.
     * This means irrelevant sections of the input can be skipped.
     */
    var suggestionRequestInfo: SuggestionRequestInfo? = null

    var previousCache: CacheData? = null
    val newCache = CacheData()

    fun canSuggestionsSkipRange(absoluteStart: Int, absoluteEnd: Int): Boolean {
        val suggestionCursor = suggestionRequestInfo?.absoluteCursor ?: return false
        return suggestionCursor !in absoluteStart..absoluteEnd
    }

    data class ResourceStackEntry(val analyzingResult: AnalyzingResult)

    class CacheData(
        var usedCommandDispatcher: CommandDispatcher<SharedSuggestionProvider>? = null,
        val vanillaMacroCache: MutableMap<List<String>, AnalyzingResult> = mutableMapOf()
    )

    class SuggestionRequestInfo(
        /**
         * The absolute cursor where the suggestion was requested
         */
        val absoluteCursor: Int,
        /**
         * `true` if the suggestions were requested through [net.minecraft.command.CommandSource.getCompletions]
         */
        val isServersideSuggestionRequest: Boolean
    )
}