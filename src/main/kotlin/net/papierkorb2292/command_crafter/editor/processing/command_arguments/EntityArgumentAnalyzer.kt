package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.selector.EntitySelectorParser
import net.minecraft.world.entity.EntityType
import net.papierkorb2292.command_crafter.editor.debugger.helper.plus
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.DataObjectDecoding
import net.papierkorb2292.command_crafter.editor.processing.helper.AllowMalformedContainer
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultDataContainer
import net.papierkorb2292.command_crafter.editor.processing.helper.IsNonPlayerSelector
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Range

class EntityArgumentAnalyzer : CommandArgumentAnalyzerService<EntityArgument> {
    override val argumentTypes: List<Class<out EntityArgument>>
        get() = listOf(EntityArgument::class.java)

    override fun analyze(
        context: CommandContext<SharedSuggestionProvider>,
        type: EntityArgument,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        result: AnalyzingResult,
    ) {
        val selectorReader = EntitySelectorParser(reader, true)
        @Suppress("KotlinConstantConditions")
        (selectorReader as AnalyzingResultDataContainer).`command_crafter$setAnalyzingResult`(result)
        (selectorReader as AllowMalformedContainer).`command_crafter$setAllowMalformed`(true)
        if((type as IsNonPlayerSelector).`command_crafter$getIsNonPlayerSelector`()) {
            val dataObjectDecoding = DataObjectDecoding.getForReader(reader)
            // Calls .parse()
            val candidates = dataObjectDecoding.getEntityChangeCandidates(selectorReader, true)
            val sourceRange = result.mappingInfo.cursorMapper.mapToSource(range + reader.readSkippingChars)
            if(candidates.size == 1 && candidates.first().type == EntityType.PLAYER) {
                result.diagnostics.add(Diagnostic(
                    Range(
                        AnalyzingResult.getPositionFromCursor(sourceRange.start, result.mappingInfo),
                        AnalyzingResult.getPositionFromCursor(sourceRange.end, result.mappingInfo),
                    ),
                    "Selector targets only players, but players can't be modified"
                ).apply {
                    severity = DiagnosticSeverity.Warning
                })
            }
        } else {
            selectorReader.parse()
        }
    }
}