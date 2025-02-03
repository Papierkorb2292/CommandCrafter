package net.papierkorb2292.command_crafter.editor.processing

import com.google.common.collect.Streams
import com.google.gson.JsonElement
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.context.StringRange
import com.mojang.datafixers.util.Pair
import com.mojang.serialization.*
import net.minecraft.nbt.*
import net.minecraft.registry.RegistryWrapper
import net.minecraft.text.TextCodecs
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree.StringEscaper
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree.StringEscaper.Companion.andThen
import net.papierkorb2292.command_crafter.editor.processing.helper.*
import net.papierkorb2292.command_crafter.helper.runWithValue
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import net.papierkorb2292.command_crafter.parser.helper.OffsetProcessedInputCursorMapper
import net.papierkorb2292.command_crafter.parser.helper.SplitProcessedInputCursorMapper
import net.papierkorb2292.command_crafter.string_range_gson.JsonReader
import net.papierkorb2292.command_crafter.string_range_gson.Strictness
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.stream.Collectors
import java.util.stream.IntStream
import java.util.stream.LongStream
import java.util.stream.Stream
import kotlin.math.min
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

/**
 * Used for storing the [StringRange]s of the nodes in a tree alongside the nodes themselves,
 * so semantic tokens and other editor language features can use them.
 */
class StringRangeTree<TNode: Any>(
    val root: TNode,
    /**
     * The nodes in the tree in the order they appear in the input string.
     */
    val orderedNodes: List<TNode>,
    /**
     * The ranges of the nodes in the tree.
     */
    val ranges: Map<TNode, StringRange>,
    /**
     * The ranges right before the start of a node, in which the node would have also been allowed to start.
     */
    val nodeAllowedStartRanges: Map<TNode, StringRange>,
    /**
     * The ranges of keys of maps/objects/compounds in the tree
     */
    val mapKeyRanges: Map<TNode, Collection<StringRange>>,
    /**
     * The ranges between entries of a node with children. Can be used for suggesting key names or list entries.
     */
    val internalNodeRangesBetweenEntries: Map<TNode, Collection<StringRange>>,
    /**
     * A set of all nodes that were inserted into the tree because a value could not be read,
     * but a representing node is needed for further processing.
     */
    val placeholderNodes: Set<TNode>,
) {
    /**
     * Flattens the list and sorts it. The lists contained in the input must already be sorted.
     */
    private fun <T> flattenSorted(list: List<List<T>>, comparator: Comparator<T>): List<T> {
        val result = mutableListOf<T>()

        fun insertAdjustLow(element: T, lowIn: Int): Int {
            // Binary search
            var low = lowIn
            var high = result.size - 1
            while (low <= high) {
                val mid = (low + high).ushr(1) // safe from overflows
                val midVal = result[mid]
                val cmp = comparator.compare(midVal, element)

                if (cmp < 0)
                    low = mid + 1
                else if (cmp > 0)
                    high = mid - 1
                else
                //Intersecting another element
                    return low + 1
            }
            result.add(low, element)
            return low
        }

        for(sorted in list) {
            var low = 0
            for((i, element) in sorted.withIndex()) {
                if(result.isEmpty() || comparator.compare(result.last(), element) < 0) {
                    result += sorted.subList(i, sorted.size)
                    break
                }
                low = insertAdjustLow(element, low)
            }
        }

        return result
    }

    private fun getNodeRangeOrThrow(node: TNode) =
        ranges[node] ?: throw IllegalStateException("Node $node not found in ranges")

    fun generateSemanticTokens(tokenProvider: SemanticTokenProvider<TNode>, builder: SemanticTokensBuilder, semanticTokenInserts: Iterator<kotlin.Pair<StringRange, SemanticTokensBuilder>>) {
        val collectedTokens = mutableListOf<List<AdditionalToken>>()
        for(node in orderedNodes) {
            val range = getNodeRangeOrThrow(node)
            val nodeTokens = tokenProvider.getNodeTokenInfo(node)?.let { listOf(AdditionalToken(range, it)) }
                ?: tokenProvider.getMapNameTokenInfo(node)?.let { tokenInfo ->
                    mapKeyRanges[node]?.map { AdditionalToken(it, tokenInfo) }
                }
            if(nodeTokens != null)
                collectedTokens += nodeTokens
            collectedTokens += tokenProvider.getAdditionalTokens(node).toList()
        }

        var nextSemanticTokensInsertRange: StringRange? = null
        var nextSemanticTokensInsert: SemanticTokensBuilder? = null
        if(semanticTokenInserts.hasNext()) {
            val insert = semanticTokenInserts.next()
            nextSemanticTokensInsertRange = insert.first
            nextSemanticTokensInsert = insert.second
        }

        for(token in flattenSorted(
            collectedTokens,
            Comparator.comparing(AdditionalToken::range, StringRange::compareTo)
        )) {
            var tokenStart = token.range.start
            while(nextSemanticTokensInsertRange != null && token.range.end >= nextSemanticTokensInsertRange.start) {
                if(tokenStart < nextSemanticTokensInsertRange.start)
                    builder.addMultiline(StringRange(tokenStart, nextSemanticTokensInsertRange.start), token.tokenInfo.type, token.tokenInfo.modifiers)
                if(tokenStart < nextSemanticTokensInsertRange.end)
                    tokenStart = nextSemanticTokensInsertRange.end
                if(nextSemanticTokensInsert != null) {
                    builder.combineWith(nextSemanticTokensInsert)
                    nextSemanticTokensInsert = null
                }
                if(token.range.end >= nextSemanticTokensInsertRange.end) {
                    if(semanticTokenInserts.hasNext()) {
                        val insert = semanticTokenInserts.next()
                        nextSemanticTokensInsertRange = insert.first
                        nextSemanticTokensInsert = insert.second
                    } else {
                        nextSemanticTokensInsertRange = null
                    }
                }
            }
            builder.addMultiline(StringRange(tokenStart, token.range.end), token.tokenInfo.type, token.tokenInfo.modifiers)
        }
        while(nextSemanticTokensInsert != null) {
            builder.combineWith(nextSemanticTokensInsert)
            nextSemanticTokensInsert =
                if(semanticTokenInserts.hasNext()) semanticTokenInserts.next().second
                else null
        }
    }

    fun suggestFromAnalyzingOps(analyzingDynamicOps: AnalyzingDynamicOps<TNode>, result: AnalyzingResult, languageServer: MinecraftLanguageServer, suggestionResolver: SuggestionResolver<TNode>, completionEscaper: StringEscaper, suggestionInserts: Iterator<kotlin.Pair<StringRange, AnalyzingResult>>? = null) {
        val copiedMappingInfo = result.mappingInfo.copy()
        val resolvedSuggestions = orderedNodes.map { node ->
            val nodeSuggestions = mutableListOf<kotlin.Pair<StringRange, List<ResolvedSuggestion>>>()
            analyzingDynamicOps.nodeStartSuggestions[node]?.let { suggestions ->
                val range = nodeAllowedStartRanges[node] ?: StringRange.at(getNodeRangeOrThrow(node).start)
                nodeSuggestions += range to suggestions.map { suggestion ->
                    suggestionResolver.resolveSuggestion(suggestion, SuggestionType.NODE_START, languageServer, range, copiedMappingInfo, completionEscaper)
                }
            }
            analyzingDynamicOps.mapKeySuggestions[node]?.let { suggestions ->
                val ranges = internalNodeRangesBetweenEntries[node] ?: throw IllegalArgumentException("Node $node not found in internal node ranges between entries")
                nodeSuggestions += ranges.map { range ->
                    range to suggestions.map { suggestion ->
                        suggestionResolver.resolveSuggestion(suggestion, SuggestionType.MAP_KEY, languageServer, range, copiedMappingInfo, completionEscaper)
                    }
                }
            }
            nodeSuggestions
        }

        var nextSuggestionInsertRange: StringRange? = null
        var nextSuggestionInsert: AnalyzingResult? = null
        if(suggestionInserts != null && suggestionInserts.hasNext()) {
            val insert = suggestionInserts.next()
            nextSuggestionInsertRange = insert.first
            nextSuggestionInsert = insert.second
        }

        val sorted = flattenSorted(
            resolvedSuggestions,
            Comparator.comparing(kotlin.Pair<StringRange, *>::first, StringRange::compareToExclusive)
        )
        for((i, suggestionEntry) in sorted.withIndex()) {
            val (range, suggestions) = suggestionEntry
            // Extending the range to the furthest any suggestion matches the input
            val extendedEndCursor = suggestions.maxOf { it.suggestionEnd }

            // Make sure to not overlap with the next entry
            val newEndCursor = if(i + 1 < sorted.size) {
                val nextRange = sorted[i + 1].first
                min(extendedEndCursor, nextRange.start)
            } else extendedEndCursor

            var suggestionStart = range.start

            while(nextSuggestionInsert != null && nextSuggestionInsertRange!!.end <= newEndCursor) {
                if(suggestionStart <= nextSuggestionInsertRange.start) {
                    val preInsertEndCursor = min(newEndCursor, nextSuggestionInsertRange.start)
                    result.addCompletionProvider(AnalyzingResult.RangedDataProvider(StringRange(suggestionStart, preInsertEndCursor)) { offset ->
                        CompletableFuture.completedFuture(suggestions.map { it.completionItemProvider(offset) })
                    }, true)
                }
                result.combineWithCompletionProviders(nextSuggestionInsert)
                suggestionStart = nextSuggestionInsertRange.end
                if(suggestionInserts!!.hasNext()) {
                    val insert = suggestionInserts.next()
                    nextSuggestionInsertRange = insert.first
                    nextSuggestionInsert = insert.second
                } else {
                    nextSuggestionInsertRange = null
                    nextSuggestionInsert = null
                }
            }

            result.addCompletionProvider(AnalyzingResult.RangedDataProvider(StringRange(suggestionStart, newEndCursor)) { offset ->
                CompletableFuture.completedFuture(suggestions.map { it.completionItemProvider(offset) })
            }, true)
        }

        while(nextSuggestionInsert != null) {
            result.combineWithCompletionProviders(nextSuggestionInsert)
            nextSuggestionInsert =
                if(suggestionInserts!!.hasNext()) suggestionInserts.next().second
                else null
        }
    }

    /**
     * Analyzes string nodes in the tree by trying to parse them as SNBT/JSON.
     * For every string node that can be parsed to SNBT/JSON, a StringRangeTree is generated.
     * Using the StringRangeTree, semantic tokens are created. Additionally, if the parsed data represents
     * a Minecraft Text (JSON Text), completions are created for it.
     *
     * @param stringGetter A function that returns the string content and a cursor mapper between the original input (source) and the string value for a node (target), or null if the node is not a string
     * @param baseAnalyzingResult The base analyzing result whose base data is copied for each string node and filled with the analyzed data for each node.
     * @return A map of the nodes that were analyzed as strings to the analyzing results for each node as well as the range of the string content in the original input. The oder of this list is the same as the order of the nodes in the tree.
     */
    fun tryAnalyzeStrings(stringGetter: (TNode) -> kotlin.Triple<String, SplitProcessedInputCursorMapper, StringEscaper>?, baseAnalyzingResult: AnalyzingResult, parentOps: TreeOperations<TNode>?, languageServer: MinecraftLanguageServer): LinkedHashMap<TNode, kotlin.Pair<StringRange, AnalyzingResult>> {
        val results = LinkedHashMap<TNode, kotlin.Pair<StringRange, AnalyzingResult>>()
        for(node in orderedNodes) {
            val (content, cursorMapper, stringEscaper) = stringGetter(node) ?: continue
            if(!content.startsWith('{') && !content.startsWith('[')) continue
            val nbtReader = StringNbtReader(StringReader(content))
            val nbtTreeBuilder = Builder<NbtElement>()
            @Suppress("UNCHECKED_CAST", "KotlinConstantConditions")
            (nbtReader as StringRangeTreeCreator<NbtElement>).`command_crafter$setStringRangeTreeBuilder`(nbtTreeBuilder)
            (nbtReader as AllowMalformedContainer).`command_crafter$setAllowMalformed`(true)
            // Content starts with '{' or '[', for which CommandSyntaxExceptions are always caught when allowing malformed
            val nbtRoot = nbtReader.parseElement()
            val nbtTree = nbtTreeBuilder.build(nbtRoot)
            val nbtElementCount = nbtTree.orderedNodes.count { it !in nbtTree.placeholderNodes }
            val jsonTree = StringRangeTreeJsonReader(java.io.StringReader(content)).read(Strictness.LENIENT, true)
            val jsonElementCount = jsonTree.orderedNodes.count { it !in jsonTree.placeholderNodes }

            val nbtArrayCount = nbtTree.orderedNodes.count {
                if(it !is AbstractNbtList<*>)
                    return@count false
                val startCursor = nbtTree.getNodeRangeOrThrow(it).start
                if(content.length <= startCursor + 2 || content[startCursor + 2] != ';')
                    return@count false
                val arrayTypeChar = content[startCursor + 1]
                arrayTypeChar == 'B' || arrayTypeChar == 'I' || arrayTypeChar == 'L'
            }

            val allKeysQuoted = nbtTree.mapKeyRanges.values.flatten().all {
                // Only check keys with a colon, so completions can be typed out without quotes
                var nextCursor = it.end
                while(nextCursor < content.length && Character.isWhitespace(content[nextCursor]))
                    nextCursor++
                if(nextCursor >= content.length || content[nextCursor] != ':')
                    return@all true
                
                // Check quotes
                val startChar = content[it.start]
                if(startChar != '"' && startChar != '\'')
                    return@all false
                true
            }

            /*
            Select the data type that was able to parse more elements.

            Because NBT arrays are of the form [T;...], the JSON parser reads an
            additional unquoted string for them that has to be accounted for when comparing element counts.

            Since many inputs can be interpreted as both NBT or lenient JSON, all keys being quoted
            (a requirement for proper JSON), is one plus point for json, meaning that if both element counts
            are the same, JSON is chosen (otherwise, when not all keys are quoted, NBT is chosen).
            */
            var treeOperations =
                if(nbtElementCount + nbtArrayCount >= jsonElementCount + if(allKeysQuoted && nbtArrayCount == 0) 1 else 0)
                    TreeOperations.forNbt(nbtTree, content)
                else
                    TreeOperations.forJson(jsonTree, content)
            if(parentOps != null)
                treeOperations = treeOperations
                    .withRegistry(parentOps.registryWrapper)
                    .withCompletionEscaper(parentOps.completionEscaper.andThen(stringEscaper))
            val offsetBaseMapper = baseAnalyzingResult.mappingInfo.cursorMapper
                .combineWith(OffsetProcessedInputCursorMapper(-baseAnalyzingResult.mappingInfo.readSkippingChars))
            offsetBaseMapper.removeNegativeTargetCursors()
            val stringMappingInfo = FileMappingInfo(
                baseAnalyzingResult.mappingInfo.lines,
                offsetBaseMapper.combineWith(cursorMapper)
            )

            val jsonTextRecognizability = tryRecognizeJsonText(treeOperations)

            val stringAnalyzingResult = AnalyzingResult(stringMappingInfo, Position())
            treeOperations.analyzeFull(
                stringAnalyzingResult,
                languageServer,
                true,
                if(jsonTextRecognizability.shouldDecode()) TextCodecs.CODEC else null
            )
            // Parse input without lenience to find syntax errors
            if(treeOperations.stringRangeTree == jsonTree) {
                val reader = JsonReader(java.io.StringReader(content))
                try {
                    StringRangeTreeJsonReader { reader }.read(Strictness.STRICT, false)
                } catch(e: Exception) {
                    stringAnalyzingResult.diagnostics += Diagnostic().also {
                        it.range = Range(
                            AnalyzingResult.getPositionFromCursor(stringMappingInfo.cursorMapper.mapToSource(reader.absolutePos), stringMappingInfo),
                            AnalyzingResult.getPositionFromCursor(stringMappingInfo.cursorMapper.mapToSource(content.length), stringMappingInfo)
                        )
                        it.message = e.message
                        it.severity = DiagnosticSeverity.Warning
                    }
                }
            } else if(treeOperations.stringRangeTree == nbtTree) {
                val reader = StringReader(content)
                try {
                    StringNbtReader(reader).parseElement()
                } catch(e: Exception) {
                    stringAnalyzingResult.diagnostics += Diagnostic().also {
                        it.range = Range(
                            AnalyzingResult.getPositionFromCursor(stringMappingInfo.cursorMapper.mapToSource(reader.cursor), stringMappingInfo),
                            AnalyzingResult.getPositionFromCursor(stringMappingInfo.cursorMapper.mapToSource(content.length), stringMappingInfo)
                        )
                        it.message = e.message
                        it.severity = DiagnosticSeverity.Warning
                    }
                }
            }

            if(jsonTextRecognizability.shouldGenerateDiagnostics()) {
                treeOperations.generateDiagnostics(stringAnalyzingResult, TextCodecs.CODEC, DiagnosticSeverity.Warning)
            }
            results[node] = cursorMapper.mapToSource(StringRange(0, content.length)) to stringAnalyzingResult
        }
        return results
    }

    private fun <TNode: Any> tryRecognizeJsonText(treeOperations: TreeOperations<TNode>): JsonTextRecognizability {
        val (accessedKeysWatcherDynamicOps, wrappedOps) = wrapDynamicOps(treeOperations.ops, ::AccessedKeysWatcherDynamicOps)
        val wrappedTreeOperations = treeOperations.copy(ops = wrappedOps)
        TextCodecs.CODEC.decode(wrappedTreeOperations.ops, treeOperations.stringRangeTree.root)
        var recognizability = JsonTextRecognizability.NOT_DETERMINABLE
        val textComponents = mutableListOf(treeOperations.stringRangeTree.root)
        while(textComponents.isNotEmpty()) {
            val textComponent = textComponents.removeAt(0)
            val tryAsList = treeOperations.ops.getList(textComponent)
            if(tryAsList.result().isPresent) {
                tryAsList.result().get().accept(textComponents::add)
                continue
            }
            val tryAsMap = treeOperations.ops.getMap(textComponent)
            if(tryAsMap.result().isEmpty) continue
            val map = tryAsMap.result().get()
            val accessedKeys = accessedKeysWatcherDynamicOps.accessedKeys[textComponent] ?: continue
            val existingKeys = map.entries()
                .filter { it.second !in treeOperations.stringRangeTree.placeholderNodes }
                .map { it.first }
                .collect(Collectors.toSet())

            if(existingKeys.isNotEmpty()) {
                if(!existingKeys.any(accessedKeys::contains))
                    // The map resembles no text component
                    return JsonTextRecognizability.NO_JSON_TEXT
                recognizability = JsonTextRecognizability.NOT_DETERMINABLE
            }
        }
        // All contents resemble a text component
        return recognizability
    }

    data class TreeOperations<TNode: Any>(
        val stringRangeTree: StringRangeTree<TNode>,
        val ops: DynamicOps<TNode>,
        val semanticTokenProvider: SemanticTokenProvider<TNode>,
        val suggestionResolver: SuggestionResolver<TNode>,
        val stringGetter: (TNode) -> kotlin.Triple<String, SplitProcessedInputCursorMapper, StringEscaper>?,
        val nodeClass: KClass<out TNode>,
        var completionEscaper: StringEscaper = StringEscaper.Identity,
        val registryWrapper: RegistryWrapper.WrapperLookup? = null,
    ) {
        // Size should never be 0, because a root element has to be present. If it is 1, there are no child elements.
        val isRootEmpty = stringRangeTree.orderedNodes.size == 1

        companion object {
            private val IDENTITY_ESCAPER: (String) -> String = { it }

            fun forJson(jsonTree: StringRangeTree<JsonElement>, content: String) =
                TreeOperations(
                    jsonTree,
                    JsonOps.INSTANCE,
                    StringRangeTreeJsonReader.StringRangeTreeSemanticTokenProvider,
                    StringRangeTreeJsonReader.StringRangeTreeSuggestionResolver(content),
                    StringRangeTreeJsonReader.StringContentGetter(jsonTree, content),
                    JsonElement::class
                )

            fun forJson(jsonTree: StringRangeTree<JsonElement>, reader: DirectiveStringReader<*>) =
                TreeOperations(
                    jsonTree,
                    JsonOps.INSTANCE,
                    StringRangeTreeJsonReader.StringRangeTreeSemanticTokenProvider,
                    StringRangeTreeJsonReader.StringRangeTreeSuggestionResolver(reader),
                    StringRangeTreeJsonReader.StringContentGetter(jsonTree, reader.string),
                    JsonElement::class
                )

            fun forNbt(nbtTree: StringRangeTree<NbtElement>, content: String) =
                TreeOperations(
                    nbtTree,
                    NbtOps.INSTANCE,
                    NbtSemanticTokenProvider(nbtTree, content),
                    NbtSuggestionResolver(content),
                    NbtStringContentGetter(nbtTree, content),
                    NbtElement::class
                )

            fun forNbt(nbtTree: StringRangeTree<NbtElement>, reader: DirectiveStringReader<*>) =
                TreeOperations(
                    nbtTree,
                    NbtOps.INSTANCE,
                    NbtSemanticTokenProvider(nbtTree, reader.string),
                    NbtSuggestionResolver(reader),
                    NbtStringContentGetter(nbtTree, reader.string),
                    NbtElement::class
                )
        }

        fun withRegistry(wrapperLookup: RegistryWrapper.WrapperLookup?)
            = copy(registryWrapper = wrapperLookup)

        fun withCompletionEscaper(escaper: StringEscaper) = copy(completionEscaper = escaper)

        fun analyzeFull(analyzingResult: AnalyzingResult, languageServer: MinecraftLanguageServer, shouldGenerateSemanticTokens: Boolean = true, contentDecoder: Decoder<*>? = null): Boolean {
            val analyzedStrings = tryAnalyzeStrings(analyzingResult, languageServer)
            if(shouldGenerateSemanticTokens) {
                generateSemanticTokens(
                    analyzingResult.semanticTokens,
                    analyzedStrings.values
                        .asSequence()
                        .map { it.first to it.second.semanticTokens }
                        .filter { !it.second.isEmpty() }
                        .iterator()
                )
            }
            val (analyzingDynamicOps, wrappedOps) = AnalyzingDynamicOps.createAnalyzingOps(stringRangeTree, registryWrapper?.getOps(ops) ?: ops)
            if(contentDecoder != null) {
                AnalyzingDynamicOps.CURRENT_ANALYZING_OPS.runWithValue(analyzingDynamicOps) {
                    contentDecoder.decode(wrappedOps, stringRangeTree.root)
                }
            }
            analyzingDynamicOps.tree.suggestFromAnalyzingOps(analyzingDynamicOps, analyzingResult, languageServer, suggestionResolver, completionEscaper, analyzedStrings.values.iterator())
            analyzingResult.diagnostics += analyzedStrings.values.flatMap { it.second.diagnostics }
            return shouldGenerateSemanticTokens || contentDecoder != null || analyzedStrings.isNotEmpty()
        }

        fun generateSemanticTokens(builder: SemanticTokensBuilder, semanticTokenInserts: Iterator<kotlin.Pair<StringRange, SemanticTokensBuilder>>) {
            stringRangeTree.generateSemanticTokens(semanticTokenProvider, builder, semanticTokenInserts)
        }

        fun tryAnalyzeStrings(baseAnalyzingResult: AnalyzingResult, languageServer: MinecraftLanguageServer): LinkedHashMap<TNode, kotlin.Pair<StringRange, AnalyzingResult>> =
            stringRangeTree.tryAnalyzeStrings(stringGetter, baseAnalyzingResult, this, languageServer)

        fun generateDiagnostics(analyzingResult: AnalyzingResult, decoder: Decoder<*>, severity: DiagnosticSeverity = DiagnosticSeverity.Error) {
            val errorCallback = stringRangeTree.DecoderErrorLeafRangesCallback(nodeClass)
            PreLaunchDecoderOutputTracker.decodeWithCallback(decoder, registryWrapper?.getOps(ops) ?: ops, stringRangeTree.root, errorCallback)
            analyzingResult.diagnostics += errorCallback.generateDiagnostics(analyzingResult.mappingInfo, severity)
        }
    }

    class AnalyzingDynamicOps<TNode: Any> private constructor(private val delegate: DynamicOps<TNode>, tree: StringRangeTree<TNode>) : DynamicOps<TNode> {
        var tree = tree
            private set
        companion object {
            val CURRENT_ANALYZING_OPS = ThreadLocal<AnalyzingDynamicOps<*>>()

            /**
             * Wraps the given dynamic ops with AnalyzingDynamicOps.
             * @see [wrapDynamicOps]
             */
            fun <TNode: Any> createAnalyzingOps(tree: StringRangeTree<TNode>, delegate: DynamicOps<TNode>): kotlin.Pair<AnalyzingDynamicOps<TNode>, DynamicOps<TNode>> =
                wrapDynamicOps(delegate) {
                    if(it is AnalyzingDynamicOps && it.tree === tree) it
                    else AnalyzingDynamicOps(it, tree)
                }

            fun <TNode: Any, TEncoded> decodeWithAnalyzingOps(delegate: DynamicOps<TNode>, input: StringRangeTree<TNode>, decoder: Decoder<TEncoded>): kotlin.Pair<StringRangeTree<TNode>, AnalyzingDynamicOps<TNode>> {
                val (analyzingDynamicOps, wrappedOps) = createAnalyzingOps(input, delegate)
                CURRENT_ANALYZING_OPS.runWithValue(analyzingDynamicOps) {
                    decoder.decode(wrappedOps, input.root)
                }
                return analyzingDynamicOps.tree to analyzingDynamicOps
            }
        }
        internal val nodeStartSuggestions = IdentityHashMap<TNode, MutableCollection<Suggestion<TNode>>>()
        internal val mapKeySuggestions = IdentityHashMap<TNode, MutableCollection<Suggestion<TNode>>>()

        fun getNodeStartSuggestions(node: TNode) =
            nodeStartSuggestions.computeIfAbsent(node) { mutableSetOf() }
        fun getMapKeySuggestions(node: TNode) =
            mapKeySuggestions.computeIfAbsent(node) { mutableSetOf() }

        override fun getBooleanValue(input: TNode): DataResult<Boolean> {
            getNodeStartSuggestions(input).run {
                add(Suggestion(delegate.createBoolean(true)))
                add(Suggestion(delegate.createBoolean(false)))
            }
            return delegate.getBooleanValue(input)
        }

        override fun getStream(input: TNode): DataResult<Stream<TNode>> {
            getNodeStartSuggestions(input).add(Suggestion(delegate.createList(Stream.empty())))
            return delegate.getStream(input).map { stream ->
                val placeholder = delegate.emptyList()
                var isEmpty = true
                Streams.concat(stream, Stream.of(placeholder)).flatMap {
                    if(it !== placeholder) {
                        isEmpty = false
                        return@flatMap Stream.of(it)
                    }
                    if(!isEmpty || !insertListPlaceholder(input, it))
                        return@flatMap Stream.empty()
                    Stream.of(it)
                }
            }
        }
        override fun getByteBuffer(input: TNode): DataResult<ByteBuffer> {
            getNodeStartSuggestions(input).add(Suggestion(delegate.createByteList(ByteBuffer.allocate(0))))
            return delegate.getByteBuffer(input)
        }
        override fun getIntStream(input: TNode): DataResult<IntStream> {
            getNodeStartSuggestions(input).add(Suggestion(delegate.createIntList(IntStream.empty())))
            return delegate.getIntStream(input)
        }
        override fun getLongStream(input: TNode): DataResult<LongStream> {
            getNodeStartSuggestions(input).add(Suggestion(delegate.createLongList(LongStream.empty())))
            return delegate.getLongStream(input)
        }
        override fun getMap(input: TNode): DataResult<MapLike<TNode>> {
            getNodeStartSuggestions(input).add(Suggestion(delegate.createMap(Collections.emptyMap())))
            return delegate.getMap(input).map { delegateMap ->
                object : MapLike<TNode> {
                    override fun get(key: TNode): TNode? {
                        val value = delegateMap.get(key)
                        if(value == null) {
                            getMapKeySuggestions(input).add(Suggestion(key))
                        }
                        return value
                    }

                    override fun get(key: String): TNode? {
                        val value = delegateMap.get(key)
                        if(value == null) {
                            getMapKeySuggestions(input).add(Suggestion(delegate.createString(key)))
                        }
                        return value
                    }

                    override fun entries(): Stream<Pair<TNode, TNode>> {
                        return delegateMap.entries()
                    }
                }
            }
        }
        override fun getList(input: TNode): DataResult<Consumer<Consumer<TNode>>> {
            getNodeStartSuggestions(input).add(Suggestion(delegate.createList(Stream.empty())))
            return delegate.getList(input).map{ list -> Consumer{ entryConsumer ->
                var isEmpty = true
                list.accept {
                    entryConsumer.accept(it)
                    isEmpty = false
                }
                if(isEmpty) {
                    val placeholder = delegate.emptyList()
                    if(insertListPlaceholder(input, placeholder))
                        entryConsumer.accept(placeholder)
                }
            } }
        }
        override fun getStringValue(input: TNode): DataResult<String> {
            getNodeStartSuggestions(input).add(Suggestion(delegate.createString("")))
            return delegate.getStringValue(input)
        }

        private val placeholders = mutableSetOf<TNode>()

        private fun insertListPlaceholder(list: TNode, placeholder: TNode): Boolean {
            if(list in placeholders) return false

            placeholders += placeholder

            val orderedNodes = tree.orderedNodes.toMutableList()
            orderedNodes.add(orderedNodes.indexOf(list) + 1, placeholder)

            val listInnerRanges = tree.internalNodeRangesBetweenEntries[list]
                ?: throw IllegalArgumentException("Node $list not found in internal node ranges between entries")
            val listInnerRange = listInnerRanges.stream().findFirst().orElseThrow {
                IllegalArgumentException("No internal node ranges between entries found for node $list")
            }
            val ranges = IdentityHashMap(tree.ranges)
            ranges[placeholder] = StringRange.at(listInnerRange.end)
            val nodeAllowedStartRanges = IdentityHashMap(tree.nodeAllowedStartRanges)
            nodeAllowedStartRanges[placeholder] = listInnerRange
            tree = StringRangeTree(
                tree.root,
                orderedNodes,
                ranges,
                nodeAllowedStartRanges,
                tree.mapKeyRanges,
                tree.internalNodeRangesBetweenEntries,
                tree.placeholderNodes + placeholder
            )
            return true
        }

        //For later: Saving the path for each node to request suggestion descriptions for keys
        override fun getMapValues(input: TNode): DataResult<Stream<Pair<TNode, TNode>>> = delegate.getMapValues(input)
        override fun getMapEntries(input: TNode): DataResult<Consumer<BiConsumer<TNode, TNode>>> =
            delegate.getMapEntries(input)

        //Just delegates
        override fun empty(): TNode = delegate.empty()
        override fun emptyMap(): TNode = delegate.emptyMap()
        override fun emptyList(): TNode = delegate.emptyList()
        override fun <U> convertTo(outputOps: DynamicOps<U>, input: TNode): U = delegate.convertTo(outputOps, input)
        override fun getNumberValue(input: TNode): DataResult<Number> = delegate.getNumberValue(input)
        override fun createNumeric(number: Number): TNode = delegate.createNumeric(number)
        override fun createByte(b: Byte): TNode = delegate.createByte(b)
        override fun createShort(s: Short): TNode = delegate.createShort(s)
        override fun createInt(i: Int): TNode = delegate.createInt(i)
        override fun createLong(l: Long): TNode = delegate.createLong(l)
        override fun createFloat(f: Float): TNode = delegate.createFloat(f)
        override fun createDouble(d: Double): TNode = delegate.createDouble(d)
        override fun createBoolean(bl: Boolean): TNode = delegate.createBoolean(bl)
        override fun createString(string: String): TNode = delegate.createString(string)
        override fun mergeToList(list: TNode, value: TNode): DataResult<TNode> = delegate.mergeToList(list, value)
        override fun mergeToList(list: TNode, values: List<TNode>): DataResult<TNode> =
            delegate.mergeToList(list, values)
        override fun mergeToMap(map: TNode, key: TNode, value: TNode): DataResult<TNode> =
            delegate.mergeToMap(map, key, value)
        override fun mergeToMap(map: TNode, values: MapLike<TNode>): DataResult<TNode> =
            delegate.mergeToMap(map, values)
        override fun mergeToMap(`object`: TNode, map: Map<TNode, TNode>): DataResult<TNode> =
            delegate.mergeToMap(`object`, map)
        override fun mergeToPrimitive(`object`: TNode, object2: TNode): DataResult<TNode> =
            delegate.mergeToPrimitive(`object`, object2)
        override fun createMap(map: Map<TNode, TNode>): TNode = delegate.createMap(map)
        override fun createMap(map: Stream<Pair<TNode, TNode>>): TNode = delegate.createMap(map)
        override fun createList(stream: Stream<TNode>): TNode = delegate.createList(stream)
        override fun createByteList(buf: ByteBuffer): TNode = delegate.createByteList(buf)
        override fun createIntList(stream: IntStream): TNode = delegate.createIntList(stream)
        override fun createLongList(stream: LongStream): TNode = delegate.createLongList(stream)
        override fun remove(input: TNode, key: String): TNode = delegate.remove(input, key)
        override fun compressMaps(): Boolean = delegate.compressMaps()
        override fun listBuilder(): ListBuilder<TNode> = ListBuilder.Builder(this)
        override fun mapBuilder(): RecordBuilder<TNode> = RecordBuilder.MapBuilder(this)
    }

    data class Suggestion<TNode>(val element: TNode)
    class ResolvedSuggestion(val suggestionEnd: Int, val completionItemProvider: (Int) -> CompletionItem)
    data class TokenInfo(val type: TokenType, val modifiers: Int)
    data class AdditionalToken(val range: StringRange, val tokenInfo: TokenInfo)

    class SimpleCompletionItemProvider(
        private val text: String,
        private val insertStart: Int,
        private val replaceEndProvider: () -> Int,
        private val mappingInfo: FileMappingInfo,
        private val languageServer: MinecraftLanguageServer,
        private val label: String = text,
        private val kind: CompletionItemKind? = null,
    ) : (Int) -> CompletionItem {
        override fun invoke(offset: Int): CompletionItem {
            // Adjusting the insert start if the cursor is before the insert start
            val adjustedInsertStart = min(insertStart + mappingInfo.readSkippingChars, offset)
            val insertStartPos = AnalyzingResult.getPositionFromCursor(
                mappingInfo.cursorMapper.mapToSource(adjustedInsertStart),
                mappingInfo
            )
            val insertEndPos = AnalyzingResult.getPositionFromCursor(
                mappingInfo.cursorMapper.mapToSource(offset),
                mappingInfo
            )
            val clampedInsertStartPos = if(insertStartPos.line < insertEndPos.line) {
                Position(insertStartPos.line, 0)
            } else {
                insertStartPos
            }
            if(!languageServer.clientCapabilities!!.textDocument.completion.completionItem.insertReplaceSupport) {
                return CompletionItem().also {
                    it.label = label
                    it.kind = kind
                    it.sortText = " $label" // Add whitespace so it appears above VSCodes suggestions
                    it.textEdit = Either.forLeft(TextEdit(Range(clampedInsertStartPos, insertEndPos), text))
                }
            }
            val replaceEndPos = AnalyzingResult.getPositionFromCursor(
                mappingInfo.cursorMapper.mapToSource(replaceEndProvider() + mappingInfo.readSkippingChars),
                mappingInfo
            )
            val clampedReplaceEndPos = if(replaceEndPos.line > insertEndPos.line) {
                Position(insertEndPos.line, mappingInfo.lines[insertEndPos.line].length)
            } else {
                replaceEndPos
            }
            return CompletionItem().also {
                it.label = label
                it.filterText = text
                it.sortText = " $label" // Add whitespace so it appears above VSCodes suggestions
                it.kind = kind
                it.textEdit = Either.forRight(InsertReplaceEdit(text, Range(clampedInsertStartPos, insertEndPos), Range(clampedInsertStartPos, clampedReplaceEndPos)))
            }
        }
    }

    interface SemanticTokenProvider<in TNode> {
        fun getMapNameTokenInfo(map: TNode): TokenInfo?
        fun getNodeTokenInfo(node: TNode): TokenInfo?
        fun getAdditionalTokens(node: TNode): Collection<AdditionalToken>
    }

    interface SuggestionResolver<TNode> {
        fun resolveSuggestion(suggestion: Suggestion<TNode>, suggestionType: SuggestionType, languageServer: MinecraftLanguageServer, suggestionRange: StringRange, mappingInfo: FileMappingInfo, stringEscaper: StringEscaper): ResolvedSuggestion
    }

    fun interface StringEscaper {
        companion object {
            fun StringEscaper.andThen(other: StringEscaper) =
                if(other == Identity) this
                else if(this == Identity) other
                else StringEscaper { string -> other.escape(this@andThen.escape(string)) }

            fun escapeForQuotes(quotes: String) =
                StringEscaper { string -> string.replace("\\", "\\\\").replace(quotes, "\\$quotes") }
        }
        object Identity : StringEscaper {
            override fun escape(string: String) = string
        }
        fun escape(string: String): String
    }

    enum class SuggestionType {
        NODE_START,
        MAP_KEY
    }

    class Builder<TNode: Any> {
        private val nodesSet = Collections.newSetFromMap(IdentityHashMap<TNode, Boolean>())
        private val orderedNodes = mutableListOf<TNode?>()
        private val nodeRanges = IdentityHashMap<TNode, StringRange>()
        private val nodeAllowedStartRanges = IdentityHashMap<TNode, StringRange>()
        private val mapKeyRanges = IdentityHashMap<TNode, MutableCollection<StringRange>>()
        private val internalNodeRangesBetweenEntries = IdentityHashMap<TNode, MutableCollection<StringRange>>()
        private val placeholderNodes = mutableSetOf<TNode>()

        /**
         * Only adds a node into the node ordering, but doesn't add a string range for it.
         * If [build] is called before [addNode] is called for the given node, a [NodeWithoutRangeError] is thrown.
         */
        fun addNodeOrder(node: TNode) {
            if(node in nodesSet) return
            nodesSet.add(node)
            orderedNodes.add(node)
        }

        /**
         * Lets you delay adding the order for a node at the current position. This is useful when the
         * node instance is only available after its children have already been added.
         *
         * Note that the placeholder must be replaced by calling the returned function before [addNode] is called for the node.
         */
        fun registerNodeOrderPlaceholder(): (replacement: TNode) -> Unit {
            val index = orderedNodes.size
            orderedNodes.add(null)
            return {
                if(nodesSet.add(it))
                    orderedNodes[index] = it
            }
        }

        fun addNode(node: TNode, range: StringRange, nodeAllowedStart: Int? = null) {
            nodeRanges[node] = range
            if(nodeAllowedStart != null) {
                if(nodeAllowedStart > range.start)
                    throw IllegalArgumentException("Node allowed start must not be after the node start")
                nodeAllowedStartRanges[node] = StringRange(nodeAllowedStart, range.start)
            }
            if(node in nodesSet) return
            nodesSet.add(node)
            orderedNodes.add(node)
        }

        fun addRangeBetweenInternalNodeEntries(node: TNode, range: StringRange) {
            internalNodeRangesBetweenEntries.computeIfAbsent(node) { mutableListOf() }.add(range)
        }

        fun addMapKeyRange(node: TNode, range: StringRange) {
            mapKeyRanges.computeIfAbsent(node) { mutableListOf() }.add(range)
        }

        fun addPlaceholderNode(node: TNode) {
            placeholderNodes += node
        }

        fun build(root: TNode): StringRangeTree<TNode> {
            for(node in orderedNodes) {
                if(node !in nodeRanges) {
                    throw NodeWithoutRangeError("No range specified for node $node")
                }
            }
            return StringRangeTree(root, orderedNodes.mapIndexed { index, it ->
                it ?: throw UnresolvedPlaceholderError("Node order placeholder not resolved at order index $index")
            }, nodeRanges, nodeAllowedStartRanges, mapKeyRanges, internalNodeRangesBetweenEntries, placeholderNodes)
        }

        class NodeWithoutRangeError(message: String) : Error(message)
        class UnresolvedPlaceholderError(message: String): Error(message)
    }

    /**
     * A decoder  callback that tracks all errors and the range of the input that caused them.
     *
     * This callback only keeps the errors that are caused by the leaf nodes of a StringRangeTree that is reduced
     * to only the nodes with errors. In other words, all errors whose range encompasses another error's
     * range are ignored
     */
    inner class DecoderErrorLeafRangesCallback(private val nodeClass: KClass<out TNode>) : PreLaunchDecoderOutputTracker.ResultCallback {
        private val errors = mutableListOf<kotlin.Pair<StringRange, String>>()

        private fun throwForPartialOverlap(range1: StringRange, range2: StringRange) {
            throw IllegalStateException("Ranges of nodes must not partially overlap. They must either not overlap or one must encompass the other. Ranges: $range1, $range2")
        }

        override fun <TInput, TResult> onError(error: DataResult.Error<TResult>, input: TInput) {
            val inputNode = nodeClass.safeCast(input) ?: return
            val range = ranges[inputNode] ?: return
            val index = errors.binarySearch { entry -> entry.first.compareToExclusive(range) }
            if(index < 0) {
                errors.add(-index - 1, range to error.message())
                return
            }
            val existingRange = errors[index].first
            if(existingRange.start <= range.start && existingRange.end >= range.end) {
                errors[index] = range to error.message()
                return
            }

            if((existingRange.start < range.start).xor(existingRange.end > range.end))
                throwForPartialOverlap(existingRange, range)
        }

        override fun <TInput, TResult> onResult(result: TResult, isPartial: Boolean, input: TInput) {
            if(isPartial) return

            //Since decoding was successful, all errors that are encompassed by the input node's range are removed
            val inputNode = nodeClass.safeCast(input) ?: return
            val range = ranges[inputNode] ?: return
            var index = errors.binarySearch { entry -> entry.first.compareToExclusive(range) }
            if(index < 0) return

            while(index >= 0) {
                val existingRange = errors[index].first
                if(existingRange.start < range.start) {
                    if(existingRange.end < range.end && existingRange.end > range.start)
                        throwForPartialOverlap(existingRange, range)
                    break
                }
                if(existingRange.end > range.end) {
                    if(existingRange.start > range.start && existingRange.start < range.end)
                        throwForPartialOverlap(existingRange, range)
                    break
                }
                errors.removeAt(index)
                if(index >= errors.size)
                    index = errors.lastIndex
            }
        }

        fun generateDiagnostics(fileMappingInfo: FileMappingInfo, severity: DiagnosticSeverity = DiagnosticSeverity.Error): List<Diagnostic> {
            return errors.map { (range, message) ->
                Diagnostic().also {
                    it.range = Range(
                        AnalyzingResult.getPositionFromCursor(fileMappingInfo.cursorMapper.mapToSource(range.start), fileMappingInfo),
                        AnalyzingResult.getPositionFromCursor(fileMappingInfo.cursorMapper.mapToSource(range.end), fileMappingInfo)
                    )
                    it.message = message
                    it.severity = severity
                }
            }
        }
    }

    private enum class JsonTextRecognizability {
        NO_JSON_TEXT,
        NOT_DETERMINABLE,
        IS_JSON_TEXT;

        fun shouldDecode() = this != NO_JSON_TEXT
        fun shouldGenerateDiagnostics() = this == IS_JSON_TEXT
    }
}