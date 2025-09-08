package net.papierkorb2292.command_crafter.parser

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.serialization.Decoder
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder
import net.minecraft.command.CommandSource
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.StringNbtReader
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.function.FunctionBuilder
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.debugger.DebugInformation
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseHandler
import net.papierkorb2292.command_crafter.editor.debugger.helper.DebugInformationContainer
import net.papierkorb2292.command_crafter.editor.debugger.helper.DebugPauseHandlerCreatorIndexConsumer
import net.papierkorb2292.command_crafter.editor.debugger.helper.DebugPauseHandlerCreatorIndexProvider
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.BreakpointManager
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.ServerBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionBreakpointLocation
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugFrame
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugInformation
import net.papierkorb2292.command_crafter.editor.processing.*
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree.TreeOperations.Companion.forNbt
import net.papierkorb2292.command_crafter.editor.processing.helper.*
import net.papierkorb2292.command_crafter.helper.toShortString
import net.papierkorb2292.command_crafter.mixin.editor.processing.IdentifierAccessor
import net.papierkorb2292.command_crafter.mixin.parser.FunctionBuilderAccessor
import net.papierkorb2292.command_crafter.parser.helper.RawResource
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.debug.Breakpoint
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.jvm.optionals.getOrNull

object LanguageManager {
    val LANGUAGES = FabricRegistryBuilder.createSimple<LanguageType>(RegistryKey.ofRegistry(Identifier.of("command_crafter", "languages"))).buildAndRegister()!!
    val DEFAULT_CLOSURE = Language.TopLevelClosure(VanillaLanguage())

    val ANALYZER_CONFIG_PATH = ".mcfunction"

    private val SKIP_DEBUG_INFORMATION = object : FunctionDebugInformation {
        override fun parseBreakpoints(
            breakpoints: Queue<ServerBreakpoint<FunctionBreakpointLocation>>,
            server: MinecraftServer,
            sourceFile: BreakpointManager.FileBreakpointSource,
            debugConnection: EditorDebugConnection,
        ): List<Breakpoint> = emptyList()

        override fun createDebugPauseHandler(debugFrame: FunctionDebugFrame) =
            DebugPauseHandler.SkipAllDummy {
                if (debugFrame.currentCommandIndex >= debugFrame.contextChains.size - 1) {
                    debugFrame.pauseContext.pauseAfterExitFrame()
                    return@SkipAllDummy
                }
                debugFrame.pauseAtSection(debugFrame.contextChains[debugFrame.currentCommandIndex + 1].topContext, 0)
            }
    }

    fun parseToVanilla(reader: DirectiveStringReader<RawZipResourceCreator>, source: ServerCommandSource, resource: RawResource, closure: Language.LanguageClosure) {
        //TODO: Keep doc comment (or all comments)
        val closureDepth = reader.closureDepth
        reader.enterClosure(closure)
        reader.resourceCreator.resourceStack.push(resource)
        while(reader.closureDepth != closureDepth) {
            val readerEnd = !reader.canRead()
            reader.currentLanguage?.parseToVanilla(reader, source, resource)
            reader.updateLanguage()
            if(readerEnd && reader.closureDepth != closureDepth) {
                throw UNCLOSED_SCOPE_EXCEPTION.create(reader.scopeStack.element().startLine)
            }
        }
        reader.resourceCreator.resourceStack.pop()
    }

    private val UNCLOSED_SCOPE_EXCEPTION = DynamicCommandExceptionType { Text.of("Encountered unclosed scope started at line $it") }

    fun parseToCommands(reader: DirectiveStringReader<ParsedResourceCreator?>, source: ServerCommandSource, closure: Language.LanguageClosure): FunctionBuilder<ServerCommandSource> {
        val closureDepth = reader.closureDepth
        val builder = FunctionBuilderAccessor.init<ServerCommandSource>()
        reader.enterClosure(closure)
        val documentation = readDocComment(reader)
        if(documentation != null)
            (builder as DocumentationContainer).`command_crafter$setDocumentation`(documentation)
        val debugInformations: MutableList<FunctionDebugInformation> = mutableListOf(SKIP_DEBUG_INFORMATION)
        while(reader.closureDepth != closureDepth) {
            (builder as DebugPauseHandlerCreatorIndexConsumer)
                .`command_crafter$setPauseHandlerCreatorIndex`(debugInformations.size)
            val readerEnd = !reader.canRead()
            reader.currentLanguage?.run {
                val debugInformation = parseToCommands(reader, source, builder)
                debugInformations += debugInformation ?: SKIP_DEBUG_INFORMATION
            }
            reader.updateLanguage()
            if(readerEnd && reader.closureDepth != closureDepth) {
                throw UNCLOSED_SCOPE_EXCEPTION.create(reader.scopeStack.element().startLine)
            }
        }
        if(debugInformations.size != 1) {
            @Suppress("UNCHECKED_CAST")
            (builder as DebugInformationContainer<FunctionBreakpointLocation, FunctionDebugFrame>)
                .`command_crafter$setDebugInformation`(
                    DebugInformation.Concat(debugInformations) {
                        if(it.contextChains.isEmpty())
                            0 //SkipAll
                        else
                            (it.currentContextChain as DebugPauseHandlerCreatorIndexProvider)
                                .`command_crafter$getPauseHandlerCreatorIndex`()
                                ?: 0 // SkipAll
                    })
        }
        return builder
    }

    fun analyse(reader: DirectiveStringReader<AnalyzingResourceCreator>, source: CommandSource, result: AnalyzingResult, closure: Language.LanguageClosure) {
        reader.resourceCreator.resourceStack.push(AnalyzingResourceCreator.ResourceStackEntry(result))
        val closureDepth = reader.closureDepth
        reader.enterClosure(closure)

        result.documentation = readAndAnalyzeDocComment(reader, result)

        while(reader.closureDepth != closureDepth) {
            if(Thread.currentThread().isInterrupted)
                return
            val readerEnd = !reader.canRead()
            reader.currentLanguage?.analyze(reader, source, result)
            reader.updateLanguage()
            if(readerEnd && reader.closureDepth != closureDepth) {
                val position = Position(reader.lines.size - 1, reader.lines.last().length)
                result.diagnostics.add(Diagnostic(
                    Range(position, position),
                    "Unclosed scope started at line ${reader.scopeStack.element().startLine}",
                    DiagnosticSeverity.Error,
                    null
                ))
                break
            }
        }

        reader.resourceCreator.resourceStack.pop()
    }

    fun readAndAnalyzeDocComment(reader: DirectiveStringReader<AnalyzingResourceCreator>, result: AnalyzingResult): String? {
        val docCommentBuilder = StringBuilder()
        val foundComment = reader.trySkipWhitespace {
            if(!reader.canRead() || reader.peek() != '#')
                return@trySkipWhitespace false
            while(reader.canRead() && reader.peek() == '#') {
                var trailingBackslashIndex = -1
                var highlightStart = reader.cursor
                //Skip all leading '#' characters
                while(reader.canRead() && reader.peek() == '#')
                    reader.skip()
                var lineStart = reader.cursor
                while(reader.canRead()) {
                    val c = reader.read()
                    if(c == '\n') {
                        if(trailingBackslashIndex == -1)
                            break
                        result.semanticTokens.addMultiline(
                            highlightStart,
                            reader.cursor - highlightStart,
                            TokenType.COMMENT,
                            0
                        )
                        docCommentBuilder.append(reader.string.subSequence(lineStart, trailingBackslashIndex))
                        trailingBackslashIndex = -1 // Reset for next line
                        reader.skipSpaces()
                        lineStart = reader.cursor
                        highlightStart = lineStart
                    }
                    if(c.isWhitespace())
                        continue
                    // Trailing whitespace after \ is allowed, but anything else resets hasTrailingBackslash back to 'false' such that
                    // a newline would end the comment
                    trailingBackslashIndex = if(c == '\\') reader.cursor - 1 else -1
                    if(c == '@') {
                        result.semanticTokens.addMultiline(
                            highlightStart,
                            reader.cursor - highlightStart - 1,
                            TokenType.COMMENT,
                            0
                        )
                        val annotationStart = reader.cursor - 1
                        while(reader.canRead() && reader.peek() != ' ' && reader.peek() != '\n')
                            reader.skip()
                        val annotationEnd = reader.cursor
                        result.semanticTokens.addMultiline(
                            annotationStart,
                            annotationEnd - annotationStart,
                            TokenType.MACRO,
                            0
                        )
                        highlightStart = annotationEnd
                        continue
                    }
                    if(c == ':') {
                        val string = reader.string
                        val pathStartCursor = reader.cursor
                        val idStart = string.subSequence(0, reader.cursor - 1)
                            .indexOfLast { !IdentifierAccessor.callIsNamespaceCharacterValid(it) } + 1
                        while(reader.canRead() && Identifier.isPathCharacterValid(reader.peek()))
                            reader.skip()
                        val idEnd = reader.cursor
                        // Only highlight ids with a non-empty namespace and path (to avoid highlighting colons in normal text)
                        if(idStart + 1 == pathStartCursor || idEnd == pathStartCursor) {
                            reader.cursor = pathStartCursor
                            continue
                        }
                        result.semanticTokens.addMultiline(
                            highlightStart,
                            idStart - highlightStart,
                            TokenType.COMMENT,
                            0
                        )
                        val idRange = StringRange(idStart, idEnd)
                        result.semanticTokens.addMultiline(idRange, TokenType.PARAMETER, 0)
                        val languageServer = reader.resourceCreator.languageServer
                        if(languageServer != null) {
                            result.addHoverProvider(AnalyzingResult.RangedDataProvider(idRange) {
                                val keywords = PackContentFileType.parseKeywords(string, idStart, idEnd).toSet()
                                languageServer.findFileAndAnalyze(
                                    Identifier.of(string.substring(idStart, idEnd)),
                                    keywords
                                ).thenCompose { analyzingResult ->
                                    if(analyzingResult == null) {
                                        CompletableFuture.completedFuture(Hover(emptyList()))
                                    } else {
                                        languageServer.hoverDocumentation(
                                            analyzingResult,
                                            analyzingResult.toFileRange(idRange)
                                        )
                                    }
                                }
                            }, true)
                            result.addDefinitionProvider(AnalyzingResult.RangedDataProvider(idRange) {
                                val client = languageServer.client
                                    ?: return@RangedDataProvider MinecraftLanguageServer.emptyDefinitionDefault
                                val keywords = PackContentFileType.parseKeywords(string, idStart, idEnd).toSet()
                                PackContentFileType.findWorkspaceResourceFromId(
                                    Identifier.of(string.substring(idStart, idEnd)),
                                    client,
                                    keywords
                                ).thenApply {
                                    Either.forLeft(
                                        if(it == null) {
                                            emptyList()
                                        } else {
                                            listOf(Location(it.second, Range(Position(), Position())))
                                        }
                                    )
                                }
                            }, true)
                        }
                        highlightStart = idEnd
                    }
                }
                result.semanticTokens.addMultiline(
                    highlightStart,
                    reader.cursor - highlightStart,
                    TokenType.COMMENT,
                    0
                )
                docCommentBuilder.append(reader.string.subSequence(lineStart, reader.cursor))
            }
            return@trySkipWhitespace true
        }
        return if(foundComment) docCommentBuilder.toString() else null
    }

    fun readDocComment(reader: DirectiveStringReader<*>): String? {
        val docCommentBuilder = StringBuilder()
        val foundComment = reader.trySkipWhitespace {
            if(!reader.canRead() || reader.peek() != '#')
                return@trySkipWhitespace false
            while(reader.canRead() && reader.peek() == '#') {
                reader.skip()
                var lineStart = reader.cursor
                var trailingBackslashIndex = -1
                while(reader.canRead()) {
                    val c = reader.read()
                    if(c == '\\') {
                        trailingBackslashIndex = reader.cursor - 1
                        continue
                    }
                    if(c == '\n') {
                        if(trailingBackslashIndex != -1) {
                            docCommentBuilder.append(reader.string.subSequence(lineStart, trailingBackslashIndex))
                            trailingBackslashIndex = -1 // Reset for next line
                            reader.skipSpaces()
                            lineStart = reader.cursor
                            continue
                        }
                        break
                    }
                    if(!c.isWhitespace()) {
                        // Only trailing whitespace is allowed after \, anything else resets hasTrailingBackslash such that
                        // a newline would end the comment
                        trailingBackslashIndex = -1
                    }
                }
                docCommentBuilder.append(reader.string.subSequence(lineStart, reader.cursor))
            }
            return@trySkipWhitespace true
        }
        return if(foundComment) docCommentBuilder.toString() else null
    }

    init {
        Registry.register(DirectiveManager.DIRECTIVES, Identifier.of("language"), object : DirectiveManager.DirectiveType {
            override fun read(reader: DirectiveStringReader<*>) {
                val languageId = reader.readUnquotedString()
                val languageType = requireNotNull(LANGUAGES.get(Identifier.of(languageId))) { "Error while parsing function: Encountered unknown language '$languageId' on line ${reader.currentLine}" }
                reader.switchLanguage(readLanguageArgs(reader, languageType))
            }

            private fun readLanguageArgs(reader: DirectiveStringReader<*>, languageType: LanguageType): Language {
                reader.skipSpaces()
                if(reader.canRead() && reader.peek() == '(') {
                    throw IllegalArgumentException("Error while parsing function: Since CommandCrafter version 0.2, language arguments are specified as SNBT instead of the previous format with enclosing parentheses on line ${reader.currentLine} (see https://github.com/Papierkorb2292/CommandCrafter/wiki/Parser#Languages).")
                }
                if(!reader.canRead() || reader.peek() == '\n') {
                    return languageType.argumentDecoder.parse(NbtOps.INSTANCE, NbtOps.INSTANCE.empty()).orThrow
                }
                val args = StringNbtReader.fromOps(NbtOps.INSTANCE).readAsArgument(reader)
                return languageType.argumentDecoder.parse(NbtOps.INSTANCE, args).orThrow
            }

            override fun readAndAnalyze(reader: DirectiveStringReader<*>, analyzingResult: AnalyzingResult) {
                val startCursor = reader.cursor
                val startPos = AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.fileMappingInfo)
                val language = try {
                    Identifier.fromCommandInput(reader)
                } catch(e: CommandSyntaxException) {
                    analyzingResult.diagnostics += Diagnostic(
                        Range(
                            startPos,
                            AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.fileMappingInfo)
                        ),
                        e.message
                    )
                    return
                }
                val languageIdEndCursor = reader.cursor

                analyzingResult.addCompletionProviderWithContinuosMapping(
                    AnalyzingResult.DIRECTIVE_COMPLETION_CHANNEL,
                    AnalyzingResult.RangedDataProvider(
                        StringRange(startCursor, languageIdEndCursor),
                        CombinedCompletionItemProvider(
                            LANGUAGES.ids.map {
                                SimpleCompletionItemProvider(
                                    it.toShortString(),
                                    startCursor,
                                    { languageIdEndCursor },
                                    analyzingResult.mappingInfo.copy(),
                                )
                            }
                        ))
                )

                val languageIdEndPos = AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.fileMappingInfo)
                val languageType = LANGUAGES.get(language)
                if(languageType == null) {
                    analyzingResult.diagnostics += Diagnostic(
                        Range(startPos, languageIdEndPos),
                        "Error while parsing function: Encountered unknown language '$language' on line ${reader.currentLine}"
                    )
                    return
                }
                analyzingResult.semanticTokens.add(startPos.line, startPos.character, languageIdEndCursor - startCursor, TokenType.DECORATOR, 0)
                reader.switchLanguage(readAndAnalyzeLanguageArgs(reader, languageType, analyzingResult) ?: return)
            }

            private fun readAndAnalyzeLanguageArgs(reader: DirectiveStringReader<*>, languageType: LanguageType, analyzingResult: AnalyzingResult): Language? {
                val languageEnd = reader.cursor

                if(reader.trySkipWhitespace(false) {
                    reader.canRead() && reader.peek() == '('
                }) {
                    val startPos = AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.fileMappingInfo)
                    reader.cursor = reader.nextLineEnd
                    val endPos = AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.fileMappingInfo)
                    analyzingResult.diagnostics += Diagnostic(
                        Range(startPos, endPos),
                        "Error while parsing function: Since CommandCrafter version 0.2, language arguments are specified as SNBT instead of the previous format with enclosing parentheses on line ${reader.currentLine} (see https://github.com/Papierkorb2292/CommandCrafter/wiki/Parser#Languages)."
                    )
                    return null
                }

                // There must be a whitespace in front of the language arguments
                if(!reader.canRead() || reader.peek() != ' ')
                    return languageType.argumentDecoder.parse(NbtOps.INSTANCE, NbtOps.INSTANCE.empty()).result().getOrNull()
                reader.skip()

                val allowMalformedReader = reader.copy()
                val nbtReader = StringNbtReader.fromOps(NbtOps.INSTANCE)
                @Suppress("KotlinConstantConditions")
                (nbtReader as AllowMalformedContainer).`command_crafter$setAllowMalformed`(true)
                val treeBuilder = StringRangeTree.Builder<NbtElement>()
                @Suppress("UNCHECKED_CAST")
                (nbtReader as StringRangeTreeCreator<NbtElement>).`command_crafter$setStringRangeTreeBuilder`(treeBuilder)
                val nbt = if(reader.canRead() && reader.peek() == '\n') {
                    val empty = NbtOps.INSTANCE.empty()
                    treeBuilder.addNode(empty, StringRange(languageEnd + 1, reader.cursor), languageEnd + 1)
                    empty
                } else {
                    try {
                        nbtReader.readAsArgument(allowMalformedReader)
                    } catch(e: CommandSyntaxException) {
                        val empty = NbtOps.INSTANCE.empty()
                        treeBuilder.addNode(empty, StringRange(languageEnd + 1, reader.cursor), languageEnd + 1)
                        empty
                    }
                }
                forNbt(
                    treeBuilder.build(nbt),
                    allowMalformedReader
                )
                    .withSuggestionResolver(NbtSuggestionResolver(allowMalformedReader::copy) { it.value.any { c -> !StringReader.isAllowedInUnquotedString(c) } })
                    .analyzeFull(analyzingResult, true, languageType.argumentDecoder)
                if(!reader.canRead() || reader.peek() == '\n') {
                    return languageType.argumentDecoder.parse(NbtOps.INSTANCE, nbt).result().getOrNull()
                }
                // Parse nbt with strict parser to mark syntax errors
                try {
                    StringNbtReader.fromOps(NbtOps.INSTANCE).readAsArgument(reader)
                } catch(e: CommandSyntaxException) {
                    val startPos = AnalyzingResult.getPositionFromCursor(e.cursor + reader.readCharacters, reader.fileMappingInfo)
                    reader.cursor = reader.nextLineEnd
                    val endPos = AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.fileMappingInfo)
                    analyzingResult.diagnostics += Diagnostic(
                        Range(startPos, endPos),
                        e.message
                    )
                }

                return languageType.argumentDecoder.parse(NbtOps.INSTANCE, nbt).result().getOrNull()
            }
        })
    }

    interface LanguageType {
        /**
         * Used to instantiate the language with the given @language arguments.
         * If no arguments are present in the directive, DynamicOps.empty() will be used.
         *
         * For advanced analyzing, use AnalyzingDynamicOps.CURRENT_ANALYZING_OPS
         */
        val argumentDecoder: Decoder<Language>
    }
}