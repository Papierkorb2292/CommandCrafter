package net.papierkorb2292.command_crafter.editor.processing.string_range_tree

import com.google.gson.JsonElement
import com.mojang.serialization.Decoder
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.JsonOps
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.papierkorb2292.command_crafter.editor.processing.BranchBehaviorProvider
import net.papierkorb2292.command_crafter.editor.processing.codecmod.ExtraDecoderBehavior
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.helper.runWithValueSwap
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import org.eclipse.lsp4j.DiagnosticSeverity

data class TreeOperations<TNode: Any>(
    val stringRangeTree: StringRangeTree<TNode>,
    override val ops: DynamicOps<TNode>,
    val suggestionResolver: StringRangeTree.SuggestionResolver<TNode>,
    val stringGetter: StringContent.StringContentGetter<TNode>,
    override val registryAccess: RegistryAccess? = null,
    val diagnosticSeverity: DiagnosticSeverity? = DiagnosticSeverity.Error,
    override val branchBehaviorProvider: BranchBehaviorProvider<TNode> = BranchBehaviorProvider.Decode
) : SchemaOperations<TNode> {
    companion object {
        val IS_ANALYZING_DECODER = ThreadLocal<Boolean>()

        fun forJson(jsonTree: StringRangeTree<JsonElement>, content: String) =
            TreeOperations(
                jsonTree,
                JsonOps.INSTANCE,
                StringRangeTreeJsonReader.StringRangeTreeSuggestionResolver(content),
                StringRangeTreeJsonReader.StringContentGetter(jsonTree, content),
            )

        fun forJson(jsonTree: StringRangeTree<JsonElement>, reader: DirectiveStringReader<*>) =
            TreeOperations(
                jsonTree,
                JsonOps.INSTANCE,
                StringRangeTreeJsonReader.StringRangeTreeSuggestionResolver(reader),
                StringRangeTreeJsonReader.StringContentGetter(jsonTree, reader.string),
            )

        fun forNbt(nbtTree: StringRangeTree<Tag>, content: String) =
            TreeOperations(
                nbtTree,
                NbtOps.INSTANCE,
                NbtSuggestionResolver(content),
                NbtStringContentGetter(nbtTree, content),
            )

        fun forNbt(nbtTree: StringRangeTree<Tag>, reader: DirectiveStringReader<*>) =
            TreeOperations(
                nbtTree,
                NbtOps.INSTANCE,
                NbtSuggestionResolver(reader),
                NbtStringContentGetter(nbtTree, reader.string),
            )
    }

    fun withRegistry(registryAccess: RegistryAccess?)
        = copy(registryAccess = registryAccess)

    fun withOps(ops: DynamicOps<TNode>) = copy(ops = ops)

    fun withSuggestionResolver(resolver: StringRangeTree.SuggestionResolver<TNode>) = copy(suggestionResolver = resolver)

    fun withDiagnosticSeverity(severity: DiagnosticSeverity?) = copy(diagnosticSeverity = severity)

    fun withBranchBehaviorProvider(branchBehaviorProvider: BranchBehaviorProvider<TNode>) = copy(branchBehaviorProvider = branchBehaviorProvider)

    fun analyzeFull(analyzingResult: AnalyzingResult, contentDecoder: Decoder<*>? = null) {
        if(contentDecoder != null) {
            val (analyzingDynamicOps, wrappedOps) = AnalyzingDynamicOps.createAnalyzingOps(this, analyzingResult)
            IS_ANALYZING_DECODER.runWithValueSwap(true) {
                ExtraDecoderBehavior.decodeWithBehavior(
                    contentDecoder,
                    wrappedOps,
                    stringRangeTree.root,
                    FirstDecoderExtraBehavior(analyzingDynamicOps)
                )
            }
            if(diagnosticSeverity != null)
                generateDiagnostics(analyzingResult, contentDecoder, diagnosticSeverity)
            val newTree = stringRangeTree.copyWithPlaceholders(analyzingDynamicOps.placeholderChildrenMap)
            newTree.suggestFromAnalyzingOps(analyzingDynamicOps, analyzingResult, suggestionResolver)
            newTree.combineAnalyzingOpsAnalyzingResult(analyzingDynamicOps, stringGetter)
        }
    }

    fun generateDiagnostics(analyzingResult: AnalyzingResult, decoder: Decoder<*>, severity: DiagnosticSeverity = DiagnosticSeverity.Error) {
        val (errorCallback, wrappedOps) = LeafErrorDecoderCallback.createErrorOps(this)
        IS_ANALYZING_DECODER.runWithValueSwap(true) {
            ExtraDecoderBehavior.decodeWithBehavior(
                decoder,
                wrappedOps,
                stringRangeTree.root,
                FirstDecoderExtraBehavior(errorCallback)
            )
        }
        errorCallback.processUnknownKeys()
        analyzingResult.diagnostics += errorCallback.generateDiagnostics(
            { stringRangeTree.getNodeOrKeyRange(it, errorCallback.accessedKeysWatcherDynamicOps) },
            analyzingResult.mappingInfo,
            severity
        )
    }

    override val root: TNode
        get() = stringRangeTree.root

    override val placeholderNodes: Set<TNode>
        get() = stringRangeTree.placeholderNodes

    override fun getParentLinks(ops: DynamicOps<TNode>) =
        stringRangeTree.getParentLinks(ops)
}