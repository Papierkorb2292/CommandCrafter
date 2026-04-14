package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.serialization.Decoder
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.NbtPathArgument
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.DataObjectSourceContainer
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.DataObjectDecoding
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.PathOperations
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.StringRangePath
import net.papierkorb2292.command_crafter.helper.runWithValue
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import org.eclipse.lsp4j.DiagnosticSeverity

class NbtPathArgumentAnalyzer : CommandArgumentAnalyzerService<NbtPathArgument> {
    companion object {
        val currentAnalyzingResult = ThreadLocal<AnalyzingResult>()
        val currentPathBuilder = ThreadLocal<StringRangePath.Builder>()
    }

    override val argumentTypes
        get() = listOf(NbtPathArgument::class.java)

    override fun analyze(
        context: CommandContext<SharedSuggestionProvider>,
        type: NbtPathArgument,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        result: AnalyzingResult,
    ) {
        val builder = StringRangePath.Builder()
        currentAnalyzingResult.runWithValue(result) {
            currentPathBuilder.runWithValue(builder) {
                try {
                    type.parse(reader)
                } catch(_: CommandSyntaxException) {}
            }
        }
        val path = builder.buildStandalone(reader.string)

        val dataObjectSource = (type as DataObjectSourceContainer).`command_crafter$getDataObjectSource`() ?: return
        val decoder: Decoder<*>? = DataObjectDecoding.getForReader(reader).getDecoderForSource(dataObjectSource, context, reader)

        PathOperations.forReader(path, reader)
            .withDiagnosticSeverity(DiagnosticSeverity.Warning)
            .withBranchBehaviorProvider(dataObjectSource.getNBTBranchBehavior())
            .analyzeFull(result, decoder)
    }
}