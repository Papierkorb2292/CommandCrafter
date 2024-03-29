package net.papierkorb2292.command_crafter.editor.processing

import com.google.gson.JsonObject
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.StringRange
import io.netty.handler.codec.DecoderException
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.argument.serialize.ArgumentSerializer
import net.minecraft.network.PacketByteBuf
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
    val COMMAND_CRAFTER_FILE_TYPE = "command_crafter_file_type"
    val shouldAddPackContentFileType = ThreadLocal<Boolean>()

    fun analyzeForId(id: Identifier, packContentFileType: PackContentFileType, range: StringRange, result: AnalyzingResult, reader: DirectiveStringReader<AnalyzingResourceCreator>) {
        result.semanticTokens.addMultiline(range, PARAMETER, 0)
        val languageServer = reader.resourceCreator.languageServer
        val fileRange = result.toFileRange(range)
        result.addHoverProvider(RangedDataProvider(range) label@{
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

    fun <A: ArgumentType<*>> wrapArgumentSerializer(argumentSerializer: ArgumentSerializer<A, ArgumentSerializer.ArgumentTypeProperties<A>>): ArgumentSerializer<A, ArgumentSerializer.ArgumentTypeProperties<A>> {
        return WrappedArgumentSerializer(argumentSerializer)
    }

    class WrappedArgumentSerializer<A: ArgumentType<*>>(val delegate: ArgumentSerializer<A, ArgumentSerializer.ArgumentTypeProperties<A>>) : ArgumentSerializer<A, ArgumentSerializer.ArgumentTypeProperties<A>> {
        override fun writePacket(properties: ArgumentSerializer.ArgumentTypeProperties<A>, buf: PacketByteBuf) {
            if(properties !is WrappedArgumentTypeProperties) {
                delegate.writePacket(properties, buf)
                return
            }
            delegate.writePacket(properties.delegate, buf)
            if(properties.packContentFileType != null && shouldAddPackContentFileType.get()) {
                buf.writeString(COMMAND_CRAFTER_FILE_TYPE)
                buf.writeEnumConstant(properties.packContentFileType)
            }
        }

        override fun fromPacket(buf: PacketByteBuf): ArgumentSerializer.ArgumentTypeProperties<A> {
            val properties = delegate.fromPacket(buf)
            buf.markReaderIndex()
            try {
                if(buf.readString() == COMMAND_CRAFTER_FILE_TYPE)
                    return WrappedArgumentTypeProperties(properties, buf.readEnumConstant(PackContentFileType::class.java))
                else
                    buf.resetReaderIndex()
            } catch (e: DecoderException) {
                buf.resetReaderIndex()
            }
            return WrappedArgumentTypeProperties(properties, null)
        }

        override fun getArgumentTypeProperties(argumentType: A): ArgumentSerializer.ArgumentTypeProperties<A> {
            val properties = delegate.getArgumentTypeProperties(argumentType)
            return WrappedArgumentTypeProperties(properties, (argumentType as? PackContentFileTypeContainer)?.`command_crafter$getPackContentFileType`())
        }

        override fun writeJson(properties: ArgumentSerializer.ArgumentTypeProperties<A>, json: JsonObject) {
            if(properties !is WrappedArgumentTypeProperties) {
                delegate.writeJson(properties, json)
                return
            }
            delegate.writeJson(properties.delegate, json)
            val packContentFileType = properties.packContentFileType ?: return
            json.addProperty(COMMAND_CRAFTER_FILE_TYPE, packContentFileType.contentTypePath)
        }

        inner class WrappedArgumentTypeProperties(val delegate: ArgumentSerializer.ArgumentTypeProperties<A>, val packContentFileType: PackContentFileType?): ArgumentSerializer.ArgumentTypeProperties<A> {
            override fun createType(commandRegistryAccess: CommandRegistryAccess?): A {
                val type = delegate.createType(commandRegistryAccess)
                if(packContentFileType != null && type is PackContentFileTypeContainer) {
                    type.`command_crafter$setPackContentFileType`(packContentFileType)
                }
                return type
            }

            override fun getSerializer(): ArgumentSerializer<A, *> {
                return this@WrappedArgumentSerializer
            }
        }
    }
}