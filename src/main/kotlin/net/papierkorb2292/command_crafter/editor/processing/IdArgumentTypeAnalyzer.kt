package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.context.StringRange
import com.mojang.serialization.Codec
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer.Companion.emptyDefinitionDefault
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType.Companion.findWorkspaceResourceFromIdAndPackContentFileType
import net.papierkorb2292.command_crafter.editor.processing.TokenType.Companion.PARAMETER
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult.RangedDataProvider
import net.papierkorb2292.command_crafter.editor.processing.helper.PackContentFileTypeContainer
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.jsonrpc.messages.Either

object IdArgumentTypeAnalyzer {
    fun analyzeForId(id: Identifier, packContentFileType: PackContentFileType, range: StringRange, result: AnalyzingResult, reader: DirectiveStringReader<AnalyzingResourceCreator>) {
        result.semanticTokens.addMultiline(range, PARAMETER, 0)
        val languageServer = reader.resourceCreator.languageServer ?: return
        val fileRange = result.toFileRange(range)
        result.addHoverProvider(RangedDataProvider(range) {
            languageServer.findFileAndGetDocs(id, packContentFileType).thenCompose { documentation ->
                languageServer.hoverDocumentation(documentation, fileRange)
            }
        }, true)
        result.addDefinitionProvider(
            RangedDataProvider(range) {
                val client = languageServer.client ?: return@RangedDataProvider emptyDefinitionDefault
                findWorkspaceResourceFromIdAndPackContentFileType(id, packContentFileType, client)
                    .thenApply { resource ->
                        Either.forLeft(
                            if(resource == null) emptyList()
                            else listOf(Location(resource, Range(Position(), Position())))
                        )
                    }
            }, true)
    }

    fun registerFileTypeAdditionalDataType() {
        ArgumentTypeAdditionalDataSerializer.registerAdditionalDataType(
            Identifier.of("command_crafter","pack_content_file_type"),
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