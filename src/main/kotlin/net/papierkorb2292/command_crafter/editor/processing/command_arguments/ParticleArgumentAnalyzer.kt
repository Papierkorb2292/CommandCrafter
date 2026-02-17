package net.papierkorb2292.command_crafter.editor.processing.command_arguments

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.ParticleArgument
import net.minecraft.core.particles.SimpleParticleType
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.nbt.TagParser
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree
import net.papierkorb2292.command_crafter.editor.processing.TokenType.Companion.PARAMETER
import net.papierkorb2292.command_crafter.editor.processing.helper.AllowMalformedContainer
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultCreator
import net.papierkorb2292.command_crafter.editor.processing.helper.StringRangeTreeCreator
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import org.eclipse.lsp4j.DiagnosticSeverity

class ParticleArgumentAnalyzer : CommandArgumentAnalyzerService<ParticleArgument> {
    override val argumentTypes: List<Class<out ParticleArgument>>
        get() = listOf(ParticleArgument::class.java)

    override fun analyze(
        context: CommandContext<SharedSuggestionProvider>,
        type: ParticleArgument,
        range: StringRange,
        name: String,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        buildContext: CommandBuildContext,
        result: AnalyzingResult,
    ) {
        val parameterDecoder = try {
            val startPos = reader.cursor
            val particleId = Identifier.read(reader)
            result.semanticTokens.addMultiline(startPos, reader.cursor - startPos, PARAMETER, 0)
            val registry = buildContext.lookup(Registries.PARTICLE_TYPE)
            if(registry.isPresent) {
                val particleType = registry.get().get(ResourceKey.create(Registries.PARTICLE_TYPE, particleId))
                if(particleType.isPresent && particleType.get().value() !is SimpleParticleType) {
                    particleType.get().value()!!.codec().decoder()
                } else null
            } else null
        } catch(_: CommandSyntaxException) {
            null
        }

        val hasNbt = reader.canRead() && reader.peek() == '{'
        if(!hasNbt) {
            // Don't read too much, since there might still be other arguments there and analyzer shouldn't skip whitespace that's not part of the node (important for macros)
            // But still try to read in NBT for suggestions and error checking
            reader.setString(reader.string.substring(0, reader.cursor))
        }

        val nbtReader = TagParser.create(NbtOps.INSTANCE)
        val treeBuilder = StringRangeTree.Builder<Tag>()
        (nbtReader as AllowMalformedContainer).`command_crafter$setAllowMalformed`(true)


        @Suppress("UNCHECKED_CAST")
        (nbtReader as StringRangeTreeCreator<Tag>).`command_crafter$setStringRangeTreeBuilder`(treeBuilder)
        (nbtReader as AnalyzingResultCreator).`command_crafter$setAnalyzingResult`(result)
        val nbt: Tag = nbtReader.parseAsArgument(reader)!!
        val tree = treeBuilder.build(nbt)

        StringRangeTree.TreeOperations.forNbt(tree, reader)
            .withDiagnosticSeverity(DiagnosticSeverity.Error)
            .analyzeFull(result, hasNbt, parameterDecoder)
    }
}