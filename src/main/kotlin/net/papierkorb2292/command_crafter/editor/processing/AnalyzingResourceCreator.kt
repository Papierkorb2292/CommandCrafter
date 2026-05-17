package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.StringRange
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.core.RegistryAccess
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.OpenFile
import net.papierkorb2292.command_crafter.editor.debugger.helper.plus
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.StringContent
import net.papierkorb2292.command_crafter.helper.IntList
import net.papierkorb2292.command_crafter.helper.binarySearch
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import java.util.*

class AnalyzingResourceCreator(
    val languageServer: MinecraftLanguageServer?,
    val sourceFunctionUri: String,
    val registries: RegistryAccess,
    val source: SharedSuggestionProvider,
    val macroTargetCursors: IntList = IntList(),
    var previousCache: CacheData? = null,
    val newCache: CacheData = CacheData(),
) {
    val resourceStack: Deque<ResourceStackEntry> = LinkedList()

    /**
     * If not null, the analyzing is done only to request suggestions at one specific position.
     * This means irrelevant sections of the input can be skipped.
     */
    var suggestionRequestInfo: SuggestionRequestInfo? = null

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

    fun copyInput() = AnalyzingResourceCreator(languageServer, sourceFunctionUri, registries, source, macroTargetCursors.copy(), previousCache, newCache)
    fun copyForMacro() = AnalyzingResourceCreator(languageServer, sourceFunctionUri, registries, source, macroTargetCursors.copy(), previousCache, CacheData(newCache.usedCommandDispatcher))

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

    fun overlayMacros(analyzingResult: AnalyzingResult, macros: MacroCache = newCache.macroCache): AnalyzingResult {
        return analyzingResult.overlayAllCompressedSorted(macros.orderedMacros.map {
            val mappedOffset = analyzingResult.mappingInfo.cursorMapper.mapToSource(it.rangeInParent.start)
            val positionOffset = AnalyzingResult.getPositionFromCursor(mappedOffset, analyzingResult.mappingInfo)
            overlayMacros(it.analyzingResult, it.children).addOffset(analyzingResult, positionOffset, mappedOffset)
        })
    }

    data class ResourceStackEntry(val analyzingResult: AnalyzingResult)

    class CacheData(
        var usedCommandDispatcher: CommandDispatcher<SharedSuggestionProvider>? = null,
        val macroCache: MacroCache = MacroCache()
    ) {
        fun copyForMacro(macroCache: MacroCache): CacheData =
            CacheData(usedCommandDispatcher, macroCache)
    }

    class MacroCache(
        /**
         * Used for caching children when only the parent changed. Allowed to contain macros that are no longer present in the input in case they reappear.
         */
        val macrosByInput: MutableMap<MacroInput, MacroNode> = mutableMapOf(),
        /**
         * Used for caching the parent when only the child changed
         */
        val orderedMacros: MutableList<MacroNode> = mutableListOf(),
        val orderedMacroStartInParent: IntList = IntList(),
        /**
         * Used for merging macros into [AnalyzingResult]
         */
        val orderedMacroAbsoluteStart: IntList = IntList()
    ) {
        fun addMacro(macro: MacroNode) {
            macrosByInput[macro.input] = macro
            orderedMacros += macro
            orderedMacroStartInParent += macro.rangeInParent.start
            orderedMacroAbsoluteStart += macro.absoluteRange.start
        }

        fun copyWithAbsoluteOffset(offset: Int): MacroCache = MacroCache(
            macrosByInput.mapValuesTo(mutableMapOf()) { it.value.copyWithAbsoluteOffset(offset) },
            orderedMacros.mapTo(mutableListOf()) { it.copyWithAbsoluteOffset(offset) },
            orderedMacroStartInParent,
            orderedMacroAbsoluteStart.map { it + offset }
        )
    }

    class MacroNode(
        val analyzingResult: AnalyzingResult,
        val input: MacroInput,
        val rangeInParent: StringRange,
        val absoluteRange: StringRange,
        val children: MacroCache,
    ) {
        fun copyForChildCacheHit(macro: DecodedMacro) = MacroNode(
            analyzingResult,
            input,
            macro.rangeInParent,
            macro.absoluteRange,
            children.copyWithAbsoluteOffset(macro.absoluteRange.start - absoluteRange.start)
        )
        fun copyWithAbsoluteOffset(offset: Int) = MacroNode(
            analyzingResult,
            input,
            rangeInParent,
            absoluteRange + offset,
            children.copyWithAbsoluteOffset(offset)
        )
    }

    data class MacroInput(val lines: List<String>, val isTemplate: Boolean, val parser: MacroParser)

    interface MacroParser {
        fun parse(reader: DirectiveStringReader<AnalyzingResourceCreator>): DecodedMacro
    }

    data class DecodedMacro(val string: StringContent, val absoluteRange: StringRange, val rangeInParent: StringRange)

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