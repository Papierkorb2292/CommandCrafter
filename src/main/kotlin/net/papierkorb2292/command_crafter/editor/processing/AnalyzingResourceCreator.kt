package net.papierkorb2292.command_crafter.editor.processing

import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import java.util.*

class AnalyzingResourceCreator(val languageServer: MinecraftLanguageServer?, val sourceFunctionUri: String) {
    val resourceStack: Deque<ResourceStackEntry> = LinkedList()

    /**
     * If not null, the analyzing is done only to request suggestions at one specific position.
     * This means irrelevant sections of the input can be skipped.
     */
    var suggestionRequestInfo: SuggestionRequestInfo? = null

    var previousCache: CacheData? = null
    val newCache = CacheData()

    constructor(
        languageServer: MinecraftLanguageServer?,
        sourceFunctionUri: String,
        topLevelAnalyzingResult: AnalyzingResult,
    ): this(languageServer, sourceFunctionUri) {
        resourceStack.push(ResourceStackEntry(topLevelAnalyzingResult))
    }

    constructor(
        languageServer: MinecraftLanguageServer?,
        sourceFunctionUri: String,
        resourceCreator: AnalyzingResourceCreator,
    ): this(languageServer, sourceFunctionUri) {
        resourceStack.clear()
        resourceStack.addAll(resourceCreator.resourceStack)
    }

    data class ResourceStackEntry(val analyzingResult: AnalyzingResult)

    class CacheData(
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