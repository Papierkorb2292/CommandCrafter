package net.papierkorb2292.command_crafter.parser

import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.function.FunctionBuilder
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.debugger.DebugInformation
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseHandler
import net.papierkorb2292.command_crafter.editor.debugger.helper.DebugInformationContainer
import net.papierkorb2292.command_crafter.editor.debugger.helper.DebugPauseHandlerCreatorIndexConsumer
import net.papierkorb2292.command_crafter.editor.debugger.helper.DebugPauseHandlerCreatorIndexProvider
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.ServerBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionBreakpointLocation
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugFrame
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugInformation
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.TokenType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.DocumentationContainer
import net.papierkorb2292.command_crafter.editor.processing.helper.advance
import net.papierkorb2292.command_crafter.mixin.editor.semantics.IdentifierAccessor
import net.papierkorb2292.command_crafter.mixin.parser.FunctionBuilderAccessor
import net.papierkorb2292.command_crafter.parser.helper.RawResource
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.debug.Breakpoint
import java.util.*

object LanguageManager {
    val LANGUAGES = FabricRegistryBuilder.createSimple<LanguageType>(RegistryKey.ofRegistry(Identifier("command_crafter", "languages"))).buildAndRegister()!!

    private val SKIP_DEBUG_INFORMATION = object : FunctionDebugInformation {
        override fun parseBreakpoints(
            breakpoints: Queue<ServerBreakpoint<FunctionBreakpointLocation>>,
            server: MinecraftServer,
            sourceReference: Int?
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
            reader.currentLanguage?.parseToVanilla(reader, source, resource)
            reader.updateLanguage()
            if(!reader.canRead() && reader.closureDepth != closureDepth) {
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
            reader.currentLanguage?.run {
                val debugInformation = parseToCommands(reader, source, builder)
                debugInformations += debugInformation ?: SKIP_DEBUG_INFORMATION
            }
            reader.updateLanguage()
            if(!reader.canRead() && reader.closureDepth != closureDepth) {
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

    fun analyse(reader: DirectiveStringReader<AnalyzingResourceCreator>, source: ServerCommandSource, result: AnalyzingResult, closure: Language.LanguageClosure) {
        reader.resourceCreator.functionStack.push(AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.lines))
        val closureDepth = reader.closureDepth
        reader.enterClosure(closure)

        result.documentation = readAndAnalyzeDocComment(reader, result)

        while(reader.closureDepth != closureDepth) {
            reader.currentLanguage?.analyze(reader, source, result)
            reader.updateLanguage()
            if(!reader.canRead() && reader.closureDepth != closureDepth) {
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

        reader.resourceCreator.functionStack.pop()
    }

    fun readAndAnalyzeDocComment(reader: DirectiveStringReader<AnalyzingResourceCreator>, result: AnalyzingResult): String? {
        if(!reader.canRead() || reader.peek() != '#')
            return null
        val docCommentBuilder = StringBuilder()
        while(reader.canRead() && reader.peek() == '#') {
            reader.skip()
            val lineStart = reader.cursor
            var commentStart = reader.absoluteCursor - 1
            while(reader.canRead()) {
                val c = reader.read()
                if(c == '\n') {
                    result.semanticTokens.addAbsoluteMultiline(commentStart, reader.absoluteCursor - commentStart, TokenType.COMMENT, 0)
                    break
                }
                if(c == ' ')
                    continue
                if(c == '@') {
                    result.semanticTokens.addAbsoluteMultiline(commentStart, reader.absoluteCursor - commentStart - 1, TokenType.COMMENT, 0)
                    val annotationStart = reader.absoluteCursor - 1
                    while(reader.canRead() && reader.peek() != ' ' && reader.peek() != '\n')
                        reader.skip()
                    val annotationEnd = reader.absoluteCursor
                    val startPosition = AnalyzingResult.getPositionFromCursor(annotationStart, reader.lines)
                    result.semanticTokens.add(
                        startPosition.line,
                        startPosition.character,
                        annotationEnd - annotationStart,
                        TokenType.MACRO,
                        0
                    )
                    commentStart = reader.absoluteCursor
                    continue
                }
                if(c == ':') {
                    val string = reader.string
                    val idStart = reader.readCharacters + string.subSequence(0, reader.cursor - 1)
                        .indexOfLast { !IdentifierAccessor.callIsNamespaceCharacterValid(it) } + 1
                    result.semanticTokens.addAbsoluteMultiline(commentStart, idStart - commentStart, TokenType.COMMENT, 0)
                    while(reader.canRead() && Identifier.isPathCharacterValid(reader.peek()))
                        reader.skip()
                    val idEnd = reader.absoluteCursor
                    val startPosition = AnalyzingResult.getPositionFromCursor(idStart, reader.lines)
                    result.semanticTokens.add(
                        startPosition.line,
                        startPosition.character,
                        idEnd - idStart,
                        TokenType.PARAMETER,
                        0
                    )
                    commentStart = idEnd
                }
            }
            docCommentBuilder.append(reader.string.subSequence(lineStart, reader.cursor))
            docCommentBuilder.append('\n')
        }
        return docCommentBuilder.toString()
    }

    fun readDocComment(reader: DirectiveStringReader<*>): String? {
        if(!reader.canRead() || reader.peek() != '#')
            return null
        val docCommentBuilder = StringBuilder()
        while(reader.canRead() && reader.peek() == '#') {
            reader.skip()
            val lineStart = reader.cursor
            while(reader.canRead()) {
                val c = reader.read()
                if(c == '\n')
                    break
            }
            docCommentBuilder.append(reader.string.subSequence(lineStart, reader.cursor))
            docCommentBuilder.append('\n')
        }
        return docCommentBuilder.toString()
    }

    init {
        Registry.register(DirectiveManager.DIRECTIVES, Identifier("language"), object : DirectiveManager.DirectiveType {
            override fun read(reader: DirectiveStringReader<*>) {
                val language = reader.readUnquotedString()
                reader.switchLanguage(
                    requireNotNull(LANGUAGES.get(Identifier(language))) { "Error while parsing function: Encountered unknown language '$language' on line ${reader.currentLine}" }
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
            }

            override fun readAndAnalyze(reader: DirectiveStringReader<*>, analyzingResult: AnalyzingResult) {
                val startPos = AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.lines)
                val language = reader.readUnquotedString()
                val languageType = LANGUAGES.get(Identifier(language))
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
                if(parsedLanguage != null)
                    reader.switchLanguage(parsedLanguage)
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