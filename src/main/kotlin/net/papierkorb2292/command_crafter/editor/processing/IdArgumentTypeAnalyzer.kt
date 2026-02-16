package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.context.StringRange
import com.mojang.serialization.Codec
import net.minecraft.resources.Identifier
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer.Companion.emptyDefinitionDefault
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType.Companion.findWorkspaceResourceFromIdAndPackContentFileType
import net.papierkorb2292.command_crafter.editor.processing.TokenType.Companion.PARAMETER
import net.papierkorb2292.command_crafter.editor.processing.helper.ActualSyntaxNode
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.PackContentFileTypeContainer
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.concurrent.CompletableFuture

object IdArgumentTypeAnalyzer {
    fun analyzeForId(id: Identifier, packContentFileType: PackContentFileType, range: StringRange, result: AnalyzingResult, reader: DirectiveStringReader<AnalyzingResourceCreator>) {
        result.semanticTokens.addMultiline(range, PARAMETER, 0)
        val languageServer = reader.resourceCreator.languageServer ?: return
        val fileRange = result.toFileRange(range)
        result.addMappedActualSyntaxNode(range, object : ActualSyntaxNode {
            override fun getHover(cursor: Int) =
                languageServer.findFileAndGetDocs(id, packContentFileType).thenCompose { documentation ->
                    languageServer.hoverDocumentation(documentation, fileRange)
                }

            override fun getDefinition(cursor: Int): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
                val client = languageServer.client ?: return emptyDefinitionDefault
                return findWorkspaceResourceFromIdAndPackContentFileType(id, packContentFileType, client)
                    .thenApply { resource ->
                        Either.forLeft(
                            if(resource == null) emptyList()
                            else listOf(Location(resource, Range(Position(), Position())))
                        )
                    }
            }
        })
    }

    fun registerFileTypeAdditionalDataType() {
        ArgumentTypeAdditionalDataSerializer.registerAdditionalDataType(
            Identifier.fromNamespaceAndPath("command_crafter","pack_content_file_type"),
            { argumentType ->
                if(argumentType is PackContentFileTypeContainer) {
                    argumentType.`command_crafter$getPackContentFileType`()
                } else null
            },
            { argumentType, packContentFileType ->
                if(argumentType is PackContentFileTypeContainer) {
                    argumentType.`command_crafter$setPackContentFileType`(packContentFileType)
                    true
                } else false
            }, PackContentFileType.PACKET_CODEC.cast(), Codec.STRING.comap { it.toString() }
        )
    }
}