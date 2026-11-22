package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import net.minecraft.commands.arguments.ResourceArgument
import net.minecraft.commands.arguments.ResourceOrTagArgument
import net.minecraft.commands.arguments.SerializableArgument
import net.minecraft.commands.suggestions.SuggestionContext
import net.minecraft.core.Holder
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.IdArgumentTypeAnalyzer
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.editor.processing.TokenType.Companion.PARAMETER
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.getArgumentOrNull
import net.papierkorb2292.command_crafter.mixin.editor.processing.ResourceArgumentAccessor
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.helper.NodeAnalyzingExecutor

class ResourceArgumentAnalyzer : CommandArgumentAnalyzerService {
    override val argumentTypes
        get() = listOf(ResourceArgument::class.java, ResourceOrTagArgument::class.java)

    override fun analyze(
        context: CommandContext<SuggestionContext>,
        type: SerializableArgument.BrigadierWrapper<*>,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        analyzingExecutor: NodeAnalyzingExecutor,
        result: AnalyzingResult,
    ) {
        val registryKey = (type.argument() as ResourceArgumentAccessor).registryKey
        val (identifier, fileType) = when(type.argument()) {
            is ResourceOrTagArgument -> {
                (context.getArgumentOrNull<ResourceOrTagArgument.Result<*>, _>(name) ?: return)
                    .unwrap().map(
                        { it.key().identifier() to PackContentFileType.getTypeForDynamicRegistry(registryKey) },
                        { it.key().location to PackContentFileType.getOrCreateTypeForRegistryTag(registryKey) }
                    )
            }
            is ResourceArgument -> {
                val id = (context.getArgumentOrNull<Holder.Reference<*>, _>(name) ?: return)
                    .key().identifier()
                id to PackContentFileType.getTypeForDynamicRegistry(registryKey)
            }
            else -> throw IllegalArgumentException("Unexpected argument type: ${type.argument()}")
        }
        if(fileType == null) {
            // If a non-dynamic registry entry is referenced
            result.semanticTokens.addMultiline(range, PARAMETER, 0)
            return
        }
        IdArgumentTypeAnalyzer.analyzeForId(
            identifier,
            fileType,
            range,
            result,
            reader
        )
    }

    private fun getFileType(type: SerializableArgument.BrigadierWrapper<*>, reader: DirectiveStringReader<AnalyzingResourceCreator>): PackContentFileType? {
        val registryKey = (type.argument() as ResourceArgumentAccessor).registryKey

        if(type.argument() is ResourceOrTagArgument && reader.canRead() && reader.peek() == '#') {
            reader.skip()
            return PackContentFileType.getOrCreateTypeForRegistryTag(registryKey)
        }

        return PackContentFileType.getTypeForDynamicRegistry(registryKey)
    }
}