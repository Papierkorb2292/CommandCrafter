package net.papierkorb2292.command_crafter.parser

import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder
import net.minecraft.command.CommandSource
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
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.editor.processing.TokenType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.DocumentationContainer
import net.papierkorb2292.command_crafter.editor.processing.helper.advance
import net.papierkorb2292.command_crafter.mixin.editor.processing.IdentifierAccessor
import net.papierkorb2292.command_crafter.mixin.parser.FunctionBuilderAccessor
import net.papierkorb2292.command_crafter.parser.helper.RawResource
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.debug.Breakpoint
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.*
import java.util.concurrent.CompletableFuture

object LanguageManager {
    val LANGUAGES = FabricRegistryBuilder.createSimple<LanguageType>(RegistryKey.ofRegistry(Identifier.of("command_crafter", "languages"))).buildAndRegister()!!
    val DEFAULT_CLOSURE = Language.TopLevelClosure(VanillaLanguage())

    private val SKIP_DEBUG_INFORMATION = object : FunctionDebugInformation {
        override fun parseBreakpoints(
            breakpoints: Queue<ServerBreakpoint<FunctionBreakpointLocation>>,
            server: MinecraftServer,
            sourceFile: BreakpointManager.FileBreakpointSource,
            debugConnection: EditorDebugConnection
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
                        val currentCommand = it.currentContextChain
                        (currentCommand as DebugPauseHandlerCreatorIndexProvider)
                            .`command_crafter$getPauseHandlerCreatorIndex`()?.run {
                                return@Concat this
                            }
                        0 //SkipAll
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
                var highlightStart = reader.cursor
                //Skip all leading '#' characters
                while(reader.canRead() && reader.peek() == '#')
                    reader.skip()
                var lineStart = reader.cursor
                while(reader.canRead()) {
                    val c = reader.read()
                    if(c == '\n') {
                        if(reader.peek(-2) != '\\') {
                            break
                        }
                        result.semanticTokens.addMultiline(
                            highlightStart,
                            reader.cursor - highlightStart,
                            TokenType.COMMENT,
                            0
                        )
                        docCommentBuilder.append(reader.string.subSequence(lineStart, reader.cursor - 2))
                        reader.skipSpaces()
                        lineStart = reader.cursor
                        highlightStart = lineStart
                    }
                    if(c == ' ')
                        continue
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
                        val idStart = string.subSequence(0, reader.cursor - 1)
                            .indexOfLast { !IdentifierAccessor.callIsNamespaceCharacterValid(it) } + 1
                        result.semanticTokens.addMultiline(
                            highlightStart,
                            idStart - highlightStart,
                            TokenType.COMMENT,
                            0
                        )
                        while(reader.canRead() && Identifier.isPathCharacterValid(reader.peek()))
                            reader.skip()
                        val idEnd = reader.cursor
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
                while(reader.canRead()) {
                    val c = reader.read()
                    if(c == '\n') {
                        if(reader.peek(-2) == '\\') {
                            docCommentBuilder.append(reader.string.subSequence(lineStart, reader.cursor - 2))
                            reader.skipSpaces()
                            lineStart = reader.cursor
                            continue
                        }
                        break
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
                val start = reader.cursor
                val language = reader.readUnquotedString()
                reader.switchLanguage(
                    requireNotNull(LANGUAGES.get(Identifier.of(language))) { "Error while parsing function: Encountered unknown language '$language' on line ${reader.currentLine}" }
                        .run {
                            reader.skipSpaces()
                            if(!reader.canRead() || reader.peek() != '(') {
                                return@run createFromArguments(emptyMap(), reader.currentLine)
                            }
                            reader.skip()
                            val args: MutableMap<String, String?> = HashMap()
                            while(reader.canRead()) {
                                reader.skipSpaces()
                                if(!reader.canRead()) {
                                    break
                                }
                                if(reader.peek() == ')') {
                                    reader.skip()
                                    return@run createFromArguments(args, reader.currentLine)
                                }
                                val parameter = reader.readUnquotedString()
                                require(parameter.isNotEmpty()) { "Error while parsing language: Expected parameter on line ${reader.currentLine}" }
                                reader.skipSpaces()
                                if(!reader.canRead()) {
                                    throw IllegalArgumentException("Error while parsing language: Unexpected end of language parameters on line ${reader.currentLine}")
                                }
                                args[parameter] = if(reader.peek() == ',' || reader.peek() == ')') {
                                    null
                                } else {
                                    reader.expect('=')
                                    reader.skipSpaces()

                                    reader.readUnquotedString().apply {
                                        require(isNotEmpty()) { "Error while parsing language: Expected argument for parameter $parameter on line ${reader.currentLine}" }
                                    }
                                }
                                reader.skipSpaces()
                                if(reader.peek() != ')')
                                    reader.expect(',')
                            }
                            throw IllegalArgumentException("Error while parsing language: Found no closing parentheses for language parameters on line ${reader.currentLine}")
                        }
                )
                reader.lastLanguageDirective = reader.string.substring(start, reader.cursor)
            }

            override fun readAndAnalyze(reader: DirectiveStringReader<*>, analyzingResult: AnalyzingResult) {
                val start = reader.cursor
                val startPos = AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.lines)
                val language = reader.readUnquotedString()
                val languageType = LANGUAGES.get(Identifier.of(language))
                if(languageType == null) {
                    analyzingResult.diagnostics += Diagnostic(
                        Range(startPos, startPos.advance(language.length)),
                        "Error while parsing function: Encountered unknown language '$language' on line ${reader.currentLine}"
                    )
                    return
                }
                analyzingResult.semanticTokens.add(startPos.line, startPos.character, language.length, TokenType.DECORATOR, 0)

                reader.skipSpaces()
                if(!reader.canRead() || reader.peek() != '(') {
                    val parsedLanguage = languageType.createFromArgumentsAndAnalyze(emptyMap(), reader.currentLine, analyzingResult, reader.lines)
                    if(parsedLanguage != null)
                        reader.switchLanguage(parsedLanguage)
                    return
                }
                reader.skip()
                val args: MutableMap<String, AnalyzingLanguageArgument> = HashMap()
                while(reader.canRead() && reader.peek() != '\n') {
                    reader.skipSpaces()
                    if(!reader.canRead()) {
                        break
                    }
                    if(reader.peek() == ')') {
                        reader.skip()
                        val parsedLanguage = languageType.createFromArgumentsAndAnalyze(args, reader.currentLine, analyzingResult, reader.lines)
                        if(parsedLanguage != null)
                            reader.switchLanguage(parsedLanguage)
                        return
                    }
                    val parameterCursor = reader.absoluteCursor
                    val parameterPos = AnalyzingResult.getPositionFromCursor(parameterCursor, reader.lines)
                    val parameter = reader.readUnquotedString()
                    if(parameter.isEmpty()) {
                        analyzingResult.diagnostics += Diagnostic(
                            Range(parameterPos, parameterPos.advance()),
                            "Error while parsing language: Expected parameter on line ${reader.currentLine}"
                        )
                        break
                    }
                    analyzingResult.semanticTokens.add(parameterPos.line, parameterPos.character, parameter.length, TokenType.PARAMETER, 0)

                    reader.skipSpaces()
                    if(!reader.canRead()) {
                        val pos = AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.lines)
                        analyzingResult.diagnostics += Diagnostic(
                            Range(pos, pos.advance()),
                            "Error while parsing language: Unexpected end of language parameters on line ${reader.currentLine}"
                        )
                        break
                    }
                    args[parameter] = if(reader.peek() == ',' || reader.peek() == ')') {
                        AnalyzingLanguageArgument(parameterCursor, null, reader.absoluteCursor)
                    } else {
                        if(!reader.canRead() || reader.peek() != '=') {
                            val pos = AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.lines)
                            analyzingResult.diagnostics += Diagnostic(
                                Range(pos, pos.advance()),
                                "Expected '='"
                            )
                            AnalyzingLanguageArgument(parameterCursor, null, reader.absoluteCursor)
                        } else {
                            reader.skip()
                            reader.skipSpaces()
                            val argCursor = reader.absoluteCursor
                            val argPos = AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.lines)
                            val arg = reader.readUnquotedString()
                            if (arg.isEmpty()) {
                                analyzingResult.diagnostics += Diagnostic(
                                    Range(argPos, argPos.advance()),
                                    "Error while parsing language: Expected argument for parameter $parameter on line ${reader.currentLine}"
                                )
                                continue
                            }
                            analyzingResult.semanticTokens.add(
                                argPos.line,
                                argPos.character,
                                arg.length,
                                TokenType.PARAMETER,
                                0
                            )
                            AnalyzingLanguageArgument(parameterCursor, arg, argCursor)
                        }
                    }
                    reader.skipSpaces()
                    if(reader.canRead() && reader.peek() != ')') {
                        if(reader.peek() != ',') {
                            val pos = AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.lines)
                            analyzingResult.diagnostics += Diagnostic(
                                Range(pos, pos.advance()),
                                "Expected ','"
                            )
                            break
                        }
                        reader.skip()
                    }
                }
                val pos = AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.lines)
                analyzingResult.diagnostics += Diagnostic(
                    Range(pos, pos.advance()),
                    "Error while parsing language: Found no closing parentheses for language parameters on line ${reader.currentLine}"
                )
                val parsedLanguage = languageType.createFromArgumentsAndAnalyze(args, reader.currentLine, analyzingResult, reader.lines)
                if(parsedLanguage != null) {
                    reader.switchLanguage(parsedLanguage)
                    reader.lastLanguageDirective = reader.string.substring(start, reader.cursor)
                }
            }
        })
    }

    interface LanguageType {
        fun createFromArguments(args: Map<String, String?>, currentLine: Int): Language
        fun createFromArgumentsAndAnalyze(args: Map<String, AnalyzingLanguageArgument>, currentLine: Int, analyzingResult: AnalyzingResult, lines: List<String>): Language?
    }

    data class AnalyzingLanguageArgument(val parameterCursor: Int, val value: String?, val valueCursor: Int) {
        fun createDiagnostic(message: String, lines: List<String>): Diagnostic {
            val startPos = AnalyzingResult.getPositionFromCursor(parameterCursor, lines)
            var length = valueCursor - parameterCursor
            if(value != null)
                length += value.length
            val endPos = startPos.advance(length)
            return Diagnostic(
                Range(startPos, endPos),
                message
            )
        }
    }
}