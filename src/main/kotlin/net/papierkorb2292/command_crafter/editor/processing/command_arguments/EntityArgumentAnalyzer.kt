package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.selector.EntitySelectorParser
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.player.Player
import net.papierkorb2292.command_crafter.editor.debugger.helper.plus
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.DataObjectDecoding
import net.papierkorb2292.command_crafter.editor.processing.helper.AllowMalformedContainer
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultDataContainer
import net.papierkorb2292.command_crafter.editor.processing.helper.IsNonPlayerSelector
import net.papierkorb2292.command_crafter.helper.runWithValueSwap
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
        val nonPlayerSelector = (type as IsNonPlayerSelector).`command_crafter$getIsNonPlayerSelector`()
        val dataObjectDecoding = DataObjectDecoding.getForReader(reader)
        val filterReader = EntitySelectorParser(reader.copy(), true)
        @Suppress("KotlinConstantConditions")
        (filterReader as AllowMalformedContainer).`command_crafter$setAllowMalformed`(true)
        val allowedEntities = try {
            dataObjectDecoding.getEntityChangeCandidates(filterReader, true)
        } catch(_: Exception) { dataObjectDecoding.dummyEntities.values }
        val selectorReader = EntitySelectorParser(reader, true)
        (selectorReader as AnalyzingResultDataContainer).`command_crafter$setAnalyzingResult`(result)
        (selectorReader as AllowMalformedContainer).`command_crafter$setAllowMalformed`(true)
        DataObjectDecoding.SELECTOR_NBT_DECODER.runWithValueSwap(
            dataObjectDecoding.getConditionDecoderForEntities(
                if(nonPlayerSelector) allowedEntities.filter { it !is Player }
                else allowedEntities
            )
        ) {
            selectorReader.parse()
        }


        if(nonPlayerSelector && allowedEntities.size == 1 && allowedEntities.first().type == EntityType.PLAYER) {
            val sourceRange = result.mappingInfo.cursorMapper.mapToSource(range + reader.readSkippingChars)
            result.diagnostics.add(
                Diagnostic(
                Range(
                    AnalyzingResult.getPositionFromCursor(sourceRange.start, result.mappingInfo),
                    AnalyzingResult.getPositionFromCursor(sourceRange.end, result.mappingInfo),
                ),
                "Selector targets only players, but players can't be modified"
            ).apply {
                severity = DiagnosticSeverity.Warning
            })
        }
    }
}