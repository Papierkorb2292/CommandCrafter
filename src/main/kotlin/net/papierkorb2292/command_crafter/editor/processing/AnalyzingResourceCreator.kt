package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.core.RegistryAccess
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.OpenFile
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.helper.IntList
import net.papierkorb2292.command_crafter.helper.binarySearch
import java.util.*

class AnalyzingResourceCreator(val languageServer: MinecraftLanguageServer?, val sourceFunctionUri: String, val registries: RegistryAccess, val source: SharedSuggestionProvider) {
    val resourceStack: Deque<ResourceStackEntry> = LinkedList()
    val macroTargetCursors: IntList = IntList()

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

    fun macroInRange(targetStart: Int, targetEndInclusive: Int): Boolean {
        var index = macroTargetCursors.binarySearch { macroTargetCursors[it].compareTo(targetStart) }
        if(index >= 0) return true // Found exact match
        // Test if the next macro after targetStart is still before targetEndInclusive
        index = -(index + 1)
        return index < macroTargetCursors.size && macroTargetCursors[index] <= targetEndInclusive
    }

    fun loadCache(file: OpenFile, dispatcher: CommandDispatcher<SharedSuggestionProvider>) {
        (file.persistentAnalyzerData as? CacheData)?.let { persistentCache ->
            if(persistentCache.usedCommandDispatcher == dispatcher)
                previousCache = persistentCache
        }
        newCache.usedCommandDispatcher = dispatcher
    }

    fun storeCache(file: OpenFile) {
        if(!Thread.currentThread().isInterrupted)
            file.persistentAnalyzerData = newCache
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
         * `true` if the suggestions were requested through [net.minecraft.commands.SharedSuggestionProvider.customSuggestion]
         */
        val isServersideSuggestionRequest: Boolean
    )
}