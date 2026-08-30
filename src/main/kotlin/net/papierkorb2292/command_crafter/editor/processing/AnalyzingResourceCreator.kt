package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.StringRange
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.core.RegistryAccess
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.OpenFile
import net.papierkorb2292.command_crafter.editor.debugger.helper.plus
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.offsetBy
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.StringContent
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.StringEscaper
import net.papierkorb2292.command_crafter.helper.IntList
import net.papierkorb2292.command_crafter.helper.binarySearch
import net.papierkorb2292.command_crafter.helper.roundUpBinarySearch
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import java.util.*

class AnalyzingResourceCreator(
    val languageServer: MinecraftLanguageServer?,
    val sourceFunctionUri: String,
    val registries: RegistryAccess,
    val source: SharedSuggestionProvider,
    val file: FileMappingInfo,
    val macroTargetCursors: IntList = IntList(),
    var previousCache: CacheData? = null,
    val newCache: CacheData = CacheData(file),
) {
    val resourceStack: Deque<ResourceStackEntry> = LinkedList()

    /**
     * If not null, the analyzing is done only to request suggestions at one specific position.
     * This means irrelevant sections of the input can be skipped.
     */
    var suggestionRequestInfo: SuggestionRequestInfo? = null

    var macroQueue: MutableList<DelayedMacro>? = null
    val macroParserStack: MutableList<MacroParserStackEntry> = mutableListOf()

    fun canSuggestionsSkipRange(absoluteStart: Int, absoluteEnd: Int): Boolean {
        val suggestionCursor = suggestionRequestInfo?.absoluteCursor ?: return false
        return suggestionCursor !in absoluteStart..absoluteEnd
    }

    fun macroInRange(targetStart: Int, targetEndInclusive: Int): Boolean {
        val index = roundUpBinarySearch(macroTargetCursors.binarySearch(targetStart))
        // Test if the next macro after targetStart is before targetEndInclusive
        return index < macroTargetCursors.size && macroTargetCursors[index] <= targetEndInclusive
    }

    fun copyInput() = AnalyzingResourceCreator(languageServer, sourceFunctionUri, registries, source, file, macroTargetCursors.copy(), previousCache, newCache).also {
        it.suggestionRequestInfo = suggestionRequestInfo
        it.macroQueue = macroQueue
        it.macroParserStack += macroParserStack
    }

    // Doesn't copy macroParserStack, because child macros don't need to know about the macro parsers of the parent
    fun copyForMacro(macroMappingInfo: FileMappingInfo, absoluteStart: Int) = AnalyzingResourceCreator(languageServer, sourceFunctionUri, registries, source, macroMappingInfo, macroTargetCursors.copy(), previousCache, CacheData(macroMappingInfo, newCache.usedCommandDispatcher)).also {
        it.suggestionRequestInfo = suggestionRequestInfo?.let { info -> SuggestionRequestInfo(info.absoluteCursor - absoluteStart, info.isServersideSuggestionRequest) }
    }

    fun loadCache(file: OpenFile, dispatcher: CommandDispatcher<SharedSuggestionProvider>) {
        (file.persistentAnalyzerData as? CacheData)?.let { persistentCache ->
            if(persistentCache.usedCommandDispatcher == dispatcher)
                previousCache = persistentCache
        }
        newCache.usedCommandDispatcher = dispatcher
    }

    fun storeCacheKeepAnalyzingResult(file: OpenFile) {
        if(!Thread.currentThread().isInterrupted) {
            newCache.analyzingResult = previousCache?.analyzingResult
            file.persistentAnalyzerData = newCache
        }
    }

    fun storeCache(file: OpenFile, analyzingResult: AnalyzingResult) {
        if(!Thread.currentThread().isInterrupted) {
            newCache.analyzingResult = analyzingResult
            file.persistentAnalyzerData = newCache
        }
    }

    fun overlayMacros(analyzingResult: AnalyzingResult, newFile: FileMappingInfo = analyzingResult.mappingInfo): AnalyzingResult =
        MacroMerger.overlayMacros(analyzingResult, newFile, newCache.macroCache)

    fun getMacroParserStartCursors(): IntList {
        val result = IntList.intListOfZeros(macroParserStack.size)
        for((i, entry) in macroParserStack.withIndex())
            result[i] = entry.absoluteStartCursor
        return result
    }

    companion object {
        fun tryAnalyseOnlyMacroModification(newReader: DirectiveStringReader<AnalyzingResourceCreator>): AnalyzingResult? {
            val resourceCreator = newReader.resourceCreator
            val prevCache = resourceCreator.previousCache ?: return null
            val prevAnalyzingResult = prevCache.analyzingResult ?: return null
            if(!MacroMerger.trackMacroModification(prevCache.file, newReader.copyWithoutMapping())) // Don't let MacroMerger affect mappings in case there is no matching cache
                return null
             return resourceCreator.overlayMacros(prevAnalyzingResult, newReader.fileMappingInfo)
        }
    }

    data class ResourceStackEntry(val analyzingResult: AnalyzingResult)

    class CacheData(
        val file: FileMappingInfo,
        var usedCommandDispatcher: CommandDispatcher<SharedSuggestionProvider>? = null,
        var analyzingResult: AnalyzingResult? = null,
        val macroCache: MacroCache = MacroCache(),
    ) {
        fun copyForMacro(macroCache: MacroCache): CacheData =
            CacheData(file, usedCommandDispatcher, analyzingResult, macroCache)
    }

    class MacroCache(
        /**
         * Used for caching children when only the parent changed. Allowed to contain macros that are no longer present in the input in case they reappear.
         */
        val macrosByInput: MutableMap<MacroInput, MacroNode> = HashMap(),
        /**
         * Used for caching the parent when only the child changed
         */
        val orderedMacros: MutableList<MacroNode> = mutableListOf(),
        /**
         * Used for searching for macros and merging macros into [AnalyzingResult]
         */
        val orderedMacroStartInParent: IntList = IntList(),
        /**
         * Stores how the size of child changed since the last time the parent was parsed. The keys
         * correspond to indices in [orderedMacros]. If an index isn't present, the macro has an offset of zero.
         */
        val childModificationOffsets: Int2ObjectMap<MacroOffset> = Int2ObjectArrayMap(),
        /**
         * Stores an additional SemanticTokensBuilder for children that the child's semantic tokens are overlayed on top
         * off (to color strings behind modified children)
         */
        val childBackgroundSemanticTokens: Int2ObjectMap<SemanticTokensBuilder> = Int2ObjectArrayMap()
    ) {
        fun addMacro(macro: MacroNode) {
            require(macro.input.parserStack.isNotEmpty()) { "Macro parser stack must not be empty"}
            macrosByInput[macro.input] = macro
            orderedMacros += macro
            orderedMacroStartInParent += macro.fileRangeInParent.start
        }
    }

    class MacroNode(
        val analyzingResult: AnalyzingResult,
        /**
         * The semantic tokens from the macro variables that are only overlayed when combining the final AnalyzingResult.
         * These are saved separately so they can be updated when a child macro is modified.
         */
        val variablesSemanticTokensOverlay: SemanticTokensBuilder?,
        val input: MacroInput,
        /**
         * The absolute range of the macro minus the absolute start position of the parent macro
         */
        val fileRangeInParent: StringRange,
        /**
         * The absolute start cursor of each [MacroParser] in the [MacroInput] minus the absolute start position of the parent macro
         */
        val parserStartCursorsInParent: IntList,
        val stringEscaper: StringEscaper,
        val children: MacroCache,
        val updatedFile: FileMappingInfo,
    ) {
        fun copyForChildCacheHit(macro: DecodedMacro, newParserStartCursors: IntList) = MacroNode(
            analyzingResult,
            variablesSemanticTokensOverlay,
            input,
            macro.absoluteRange,
            newParserStartCursors,
            macro.string.escaper,
            children,
            updatedFile
        )

        fun withOffset(startCursor: Int, offset: Int) = MacroNode(
            analyzingResult,
            variablesSemanticTokensOverlay,
            input,
            fileRangeInParent + offset,
            parserStartCursorsInParent.map {
                if(it > startCursor) it + offset else it
            },
            stringEscaper,
            children,
            updatedFile
        )

        override fun toString() = input.lines.joinToString("\n")
    }

    data class MacroInput(val lines: List<String>, val parserStack: List<MacroParser>, val isTemplate: Boolean, val hasTemplatePrefix: Boolean, val addMissingVariablesError: Boolean, val illegalChatCharactersSeverity: DiagnosticSeverity?)

    data class DelayedMacro(val input: MacroInput, val macro: DecodedMacro, val parserStartCursors: IntList, val stringEscaper: StringEscaper, val cache: MacroCache?, val reader: DirectiveStringReader<AnalyzingResourceCreator>)

    data class MacroOffset(val cursorOffset: Int, val fileOffset: Position) {
        fun addAfter(after: MacroOffset) = MacroOffset(cursorOffset + after.cursorOffset, fileOffset.offsetBy(after.fileOffset))
        fun isNonZero() = cursorOffset != 0 || fileOffset.line != 0 || fileOffset.character != 0
    }

    interface MacroParser {
        fun parse(reader: DirectiveStringReader<AnalyzingResourceCreator>): DecodedMacro?
    }

    data class DecodedMacro(val string: StringContent, val absoluteRange: StringRange, val backgroundTokens: SemanticTokensBuilder? = null)

    data class MacroParserStackEntry(val parser: MacroParser, val escaper: StringEscaper, val absoluteStartCursor: Int)

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