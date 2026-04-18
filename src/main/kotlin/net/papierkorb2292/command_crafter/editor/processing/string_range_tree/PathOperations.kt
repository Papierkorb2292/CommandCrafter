package net.papierkorb2292.command_crafter.editor.processing.string_range_tree

import com.mojang.brigadier.context.StringRange
import com.mojang.serialization.Decoder
import com.mojang.serialization.DynamicOps
import it.unimi.dsi.fastutil.chars.CharSet
import net.minecraft.nbt.*
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.BranchBehaviorProvider
import net.papierkorb2292.command_crafter.editor.processing.StreamCompletionItemProvider
import net.papierkorb2292.command_crafter.editor.processing.codecmod.ExtraDecoderBehavior
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.TreeOperations.Companion.IS_ANALYZING_DECODER
import net.papierkorb2292.command_crafter.helper.concatNullable
import net.papierkorb2292.command_crafter.helper.runWithValueSwap
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Range
import java.util.*
import java.util.stream.Stream

data class PathOperations(
    val path: StringRangePath,
    val input: String,
    val suggestionResolver: StringRangeTree.SuggestionResolver<Tag>,
    override val reader: DirectiveStringReader<AnalyzingResourceCreator>,
    val diagnosticSeverity: DiagnosticSeverity? = DiagnosticSeverity.Error,
    override val branchBehaviorProvider: BranchBehaviorProvider<Tag> = BranchBehaviorProvider.Decode
) : SchemaOperations<Tag> {
    companion object {
        private val keyCharactersRequireQuoted = CharSet.of(' ', '"', '\'', '[', ']', '.', '{', '}')

        fun forReader(path: StringRangePath, reader: DirectiveStringReader<AnalyzingResourceCreator>) =
            PathOperations(path, reader.string, NbtSuggestionResolver(reader) { false }, reader)
    }

    val nodeToKeySegment = path.segments.filter { it.key != null }.associateByTo(IdentityHashMap()) { it.tree.root }

    fun withDiagnosticSeverity(severity: DiagnosticSeverity?) = copy(diagnosticSeverity = severity)

    fun withBranchBehaviorProvider(branchBehaviorProvider: BranchBehaviorProvider<Tag>) = copy(branchBehaviorProvider = branchBehaviorProvider)

    fun analyzeFull(analyzingResult: AnalyzingResult, contentDecoder: Decoder<*>?) {
        if(contentDecoder != null) {
            val (analyzingDynamicOps, wrappedOps) = AnalyzingDynamicOps.createAnalyzingOps(this, analyzingResult)
            IS_ANALYZING_DECODER.runWithValueSwap(true) {
                ExtraDecoderBehavior.decodeWithBehavior(
                    contentDecoder,
                    wrappedOps,
                    path.root,
                    FirstDecoderExtraBehavior(analyzingDynamicOps)
                )
            }
            if(diagnosticSeverity != null)
                generateDiagnostics(analyzingResult, contentDecoder, diagnosticSeverity)
            for(segment in path.segments) {
                val newTree = segment.tree.copyWithPlaceholders(analyzingDynamicOps.placeholderChildrenMap)
                newTree.suggestFromAnalyzingOps(analyzingDynamicOps, analyzingResult, FilterSuggestionResolver(segment), true) // Allow missing internal ranges, because END tags could have been replaced by compounds and lists
                if(segment.isFilter()) {
                    // TODO: Also analyze path keys (only matters for custom mcdoc)
                    newTree.combineAnalyzingOpsAnalyzingResult(analyzingDynamicOps, NbtStringContentGetter(newTree, input))
                }
                suggestKeys(segment, analyzingResult, analyzingDynamicOps)
            }
        }
        addCollisionDiagnostics(analyzingResult)
    }

    fun generateDiagnostics(analyzingResult: AnalyzingResult, decoder: Decoder<*>, severity: DiagnosticSeverity = DiagnosticSeverity.Error) {
        val (errorCallback, wrappedOps) = LeafErrorDecoderCallback.createErrorOps(this)
        IS_ANALYZING_DECODER.runWithValueSwap(true) {
            ExtraDecoderBehavior.decodeWithBehavior(
                decoder,
                wrappedOps,
                path.root,
                FirstDecoderExtraBehavior(errorCallback)
            )
        }
        errorCallback.finishDiagnostics()
        // Add diagnostics for trees
        for(segment in path.segments) {
            if(!segment.isFilter()) continue
            analyzingResult.diagnostics += errorCallback.generateDiagnostics(
                { segment.tree.getNodeOrKeyRange(it, errorCallback.accessedKeysWatcherDynamicOps) },
                analyzingResult.mappingInfo,
                severity
            )
        }
        // Add diagnostics for segment keys
        analyzingResult.diagnostics += errorCallback.generateDiagnostics(
            {
                val map = errorCallback.accessedKeysWatcherDynamicOps.keyToMap[it] ?: it
                val segment = nodeToKeySegment[map]
                if(segment == null || segment.isTrailing) // Diagnostic doesn't matter, because a trailing segment doesn't change the behavior of the path
                    return@generateDiagnostics null
                segment.range
            },
            analyzingResult.mappingInfo,
            severity
        )
    }

    private fun suggestKeys(segment: StringRangePath.Segment, analyzingResult: AnalyzingResult, analyzingDynamicOps: AnalyzingDynamicOps<Tag>) {
        val keySuggestions = analyzingDynamicOps.mapKeySuggestions[segment.tree.root]
            .concatNullable(analyzingDynamicOps.accessedKeysWatcher?.accessedKeys[segment.tree.root]
                ?.flatMap { mapKeyNode -> analyzingDynamicOps.nodeStartSuggestions[mapKeyNode] ?: emptyList() })
        if(!keySuggestions.isNullOrEmpty() && segment.key != null) {
            analyzingResult.addContinuouslyMappedPotentialSyntaxNode(
                AnalyzingResult.LANGUAGE_COMPLETION_CHANNEL,
                StringRange(segment.range.start, segment.range.end),
                StreamCompletionItemProvider(
                    segment.range.start,
                    { segment.range.end },
                    analyzingResult.mappingInfo,
                    CompletionItemKind.Property
                ) {
                    keySuggestions.stream().flatMap { it.getValue() }.distinct()
                        .filter { !(it.element is StringTag && it.element.value.isEmpty()) }
                        .map { suggestion ->
                            val key = (suggestion.element as? StringTag)?.value ?: suggestion.element.toString()
                            val escapedKey = escapePathKey(key)
                            StreamCompletionItemProvider.Completion(escapedKey, key, suggestion.completionModifier)
                        }
                }
            )
        }
    }

    private fun escapePathKey(key: String): String {
        if(key.none { it in keyCharactersRequireQuoted })
            return key
        val singleQuoteCount = key.count { it == '\'' }
        val doubleQuoteCount = key.count { it == '"' }
        val quoteChar = if(singleQuoteCount <= doubleQuoteCount) '\'' else '"'
        val escapedKey = key.replace("\\", "\\\\").replace(quoteChar.toString(), "\\$quoteChar")
        return "$quoteChar$escapedKey$quoteChar"
    }

    private fun addCollisionDiagnostics(analyzingResult: AnalyzingResult) {
        val mappingInfo = analyzingResult.mappingInfo
        for(collision in path.collisions) {
            val presentValue = when(collision.present) {
                is CompoundTag -> "a compound tag"
                is ListTag -> {
                    val typeHint = path.typeHints[collision.present] ?: StringRangeTree.NodeTypeHint.LIST
                    val lengthMessage = if(typeHint != StringRangeTree.NodeTypeHint.LIST) " of length ${collision.present.size}" else ""
                    "${typeHint.typeNameWithArticle} tag${lengthMessage}"
                }
                else -> collision.present.toString()
            }
            analyzingResult.diagnostics += Diagnostic(
                Range(
                    AnalyzingResult.getPositionFromCursor(mappingInfo.cursorMapper.mapToSource(collision.range.start + mappingInfo.readSkippingChars), mappingInfo),
                    AnalyzingResult.getPositionFromCursor(mappingInfo.cursorMapper.mapToSource(collision.range.end + mappingInfo.readSkippingChars), mappingInfo),
                ),
                "Path is impossible to fulfill. Value is already known to be $presentValue"
            ).apply {
                severity = DiagnosticSeverity.Warning
            }
        }
    }

    override val root: Tag
        get() = path.root

    override val ops: DynamicOps<Tag>
        get() = NbtOps.INSTANCE

    override val placeholderNodes: Set<Tag>
        get() = path.placeholderNodes
    override val typeHints: Map<Tag, StringRangeTree.NodeTypeHint>
        get() = path.typeHints

    override fun getParentLinks(ops: DynamicOps<Tag>) =
        path.getParentLinks(ops)

    inner class FilterSuggestionResolver(val segment: StringRangePath.Segment) : StringRangeTree.SuggestionResolver<Tag> {
        private val segmentRoot = segment.tree.root
        private val segmentListChild = (segmentRoot as? ListTag)?.firstOrNull()

        override fun resolveNodeSuggestion(
            suggestionProviders: Collection<ExtraDecoderBehavior.PossibleValue.Provider<Tag>>,
            tree: StringRangeTree<Tag>,
            node: Tag,
            suggestionRange: StringRange,
            mappingInfo: FileMappingInfo,
        ): StringRangeTree.ResolvedSuggestion? {
            val filteredSuggestions = when(node) {
                // Allow map filter, list filter or dot (for maps)
                segmentRoot -> suggestionProviders.map { provider -> ExtraDecoderBehavior.PossibleValue.Provider {
                    provider.getValue().flatMap {
                        when(it.element) {
                            is CompoundTag -> {
                                // Suggest dot as string. The suggestion resolver is configured to suggest it without quotes
                                if(segment.allowsCompoundFilter)
                                    Stream.of(it, ExtraDecoderBehavior.PossibleValue(StringTag.valueOf(".")))
                                else
                                    Stream.of(ExtraDecoderBehavior.PossibleValue(StringTag.valueOf(".")))
                            }
                            is ListTag -> if(it.element.isEmpty) Stream.of(it) else Stream.of()
                            else -> Stream.of()
                        }
                    }
                } }
                // Only allow map inside list filter
                segmentListChild -> suggestionProviders.map { provider -> ExtraDecoderBehavior.PossibleValue.Provider { provider.getValue().filter { it.element is CompoundTag } } }
                else -> suggestionProviders
            }
            return suggestionResolver.resolveNodeSuggestion(filteredSuggestions, tree, node, suggestionRange, mappingInfo)
        }

        override fun resolveMapKeySuggestion(
            suggestionProviders: Collection<ExtraDecoderBehavior.PossibleValue.Provider<Tag>>,
            tree: StringRangeTree<Tag>,
            map: Tag,
            suggestionRange: StringRange,
            mappingInfo: FileMappingInfo,
        ): StringRangeTree.ResolvedSuggestion? {
            return if(segment.isFilter()) {
                suggestionResolver.resolveMapKeySuggestion(suggestionProviders, tree, map, suggestionRange, mappingInfo)
            } else {
                null
            }
        }
    }
}