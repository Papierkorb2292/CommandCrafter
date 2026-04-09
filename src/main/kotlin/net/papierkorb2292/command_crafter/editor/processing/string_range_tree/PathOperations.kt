package net.papierkorb2292.command_crafter.editor.processing.string_range_tree

import com.mojang.brigadier.context.StringRange
import com.mojang.serialization.Decoder
import com.mojang.serialization.DynamicOps
import it.unimi.dsi.fastutil.chars.CharSet
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.*
import net.papierkorb2292.command_crafter.editor.processing.BranchBehaviorProvider
import net.papierkorb2292.command_crafter.editor.processing.StreamCompletionItemProvider
import net.papierkorb2292.command_crafter.editor.processing.codecmod.ExtraDecoderBehavior
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.TreeOperations.Companion.IS_ANALYZING_DECODER
import net.papierkorb2292.command_crafter.helper.concatNullable
import net.papierkorb2292.command_crafter.helper.runWithValueSwap
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Range
import java.util.*

data class PathOperations(
    val path: StringRangePath,
    override val registryAccess: RegistryAccess? = null,
    val diagnosticSeverity: DiagnosticSeverity? = DiagnosticSeverity.Error,
    override val branchBehaviorProvider: BranchBehaviorProvider<Tag> = BranchBehaviorProvider.Decode
) : SchemaOperations<Tag> {
    companion object {
        private val keyCharactersRequireQuoted = CharSet.of(' ', '"', '\'', '[', ']', '.', '{', '}')
    }

    val nodeToKeySegment = path.segments.filter { it.key != null }.associateByTo(IdentityHashMap()) { it.tree.root }

    fun withRegistry(registryAccess: RegistryAccess?)
            = copy(registryAccess = registryAccess)

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
            // TODO: Analyze tree filtered (only suggest compound if applicable)
            suggestKeys(analyzingResult, analyzingDynamicOps)
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
        errorCallback.processUnknownKeys()
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

    private fun suggestKeys(analyzingResult: AnalyzingResult, analyzingDynamicOps: AnalyzingDynamicOps<Tag>) {
        for(segment in path.segments) {
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
                        keySuggestions.stream().flatMap { it.getValue().distinct() }
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
                is ListTag -> "a list tag" // TODO: Should convert arrays to string, make sure to include array type
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

    override fun getParentLinks(ops: DynamicOps<Tag>) =
        path.getParentLinks(ops)
}