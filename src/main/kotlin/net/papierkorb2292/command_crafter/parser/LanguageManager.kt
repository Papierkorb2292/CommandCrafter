package net.papierkorb2292.command_crafter.parser

import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder
import net.minecraft.registry.Registry
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.function.CommandFunction
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.debugger.DebugInformation
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseHandler
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseHandlerFactory
import net.papierkorb2292.command_crafter.editor.debugger.helper.FunctionDebugPauseHandlerCreatorContainer
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugInformation
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionPauseContext
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.TokenType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.advance
import net.papierkorb2292.command_crafter.mixin.editor.debugger.CommandElementAccessor
import net.papierkorb2292.command_crafter.mixin.editor.debugger.CommandFunctionManagerEntryAccessor
import net.papierkorb2292.command_crafter.parser.helper.RawResource
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

object LanguageManager {
    val LANGUAGES = FabricRegistryBuilder.createSimple<LanguageType>(null, Identifier("command_crafter", "languages")).buildAndRegister()!!

    private val SKIP_DEBUG_PAUSE_HANDLER_CREATOR = object : DebugPauseHandlerFactory<FunctionPauseContext> {
        override fun createDebugPauseHandler(pauseContext: FunctionPauseContext)
            = DebugPauseHandler.SkipAllDummy {
                val nextCommands = pauseContext.executionQueue.iterator()
                while(nextCommands.hasNext()) {
                    val nextCommand = nextCommands.next()
                    val nextElement = (nextCommand as CommandFunctionManagerEntryAccessor).element
                    if(nextElement is CommandElementAccessor) {
                        val parsed = nextElement.parsed
                        pauseContext.pauseAtCommandSection(parsed, 0)
                        return@SkipAllDummy
                    }
                }
                pauseContext.stepOutOfFunction()
            }
    }

    fun parseToVanilla(reader: DirectiveStringReader<RawZipResourceCreator>, source: ServerCommandSource, resource: RawResource, closure: Language.LanguageClosure) {
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

    fun parseToCommands(reader: DirectiveStringReader<ParsedResourceCreator?>, source: ServerCommandSource, closure: Language.LanguageClosure)
        = parseToCommandsWithDebugInformation(reader, source, closure).first

    fun parseToCommandsWithDebugInformation(reader: DirectiveStringReader<ParsedResourceCreator?>, source: ServerCommandSource, closure: Language.LanguageClosure): Pair<Array<CommandFunction.Element>, FunctionDebugInformation?> {
        val closureDepth = reader.closureDepth
        val result: MutableList<CommandFunction.Element> = ArrayList()
        reader.enterClosure(closure)
        val debugInformations: MutableList<FunctionDebugInformation> = ArrayList()
        while(reader.closureDepth != closureDepth) {
            reader.currentLanguage?.run {
                val parsedLanguage = parseToCommands(reader, source)
                result.addAll(parsedLanguage.first)
                parsedLanguage.second?.run {
                    debugInformations += this
                    for(element in parsedLanguage.first) {
                        if(element is FunctionDebugPauseHandlerCreatorContainer) {
                            element.`command_crafter$setHandlerCreator`(this)
                        }
                    }
                }
            }
            reader.updateLanguage()
            if(!reader.canRead() && reader.closureDepth != closureDepth) {
                throw UNCLOSED_SCOPE_EXCEPTION.create(reader.scopeStack.element().startLine)
            }
        }
        return result.toTypedArray() to
                if(debugInformations.isEmpty()) null
                else DebugInformation.Concat(debugInformations) {
                    val currentCommand = it.currentCommand
                    if (currentCommand is FunctionDebugPauseHandlerCreatorContainer) {
                        val pauseHandlerCreator = currentCommand.`command_crafter$getPauseHandlerCreator`()
                        if (pauseHandlerCreator != null) return@Concat pauseHandlerCreator
                    }
                    SKIP_DEBUG_PAUSE_HANDLER_CREATOR
                }
    }

    fun analyse(reader: DirectiveStringReader<AnalyzingResourceCreator>, source: ServerCommandSource, result: AnalyzingResult, closure: Language.LanguageClosure) {
        reader.resourceCreator.functionStack.push(AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.lines))
        val closureDepth = reader.closureDepth
        reader.enterClosure(closure)
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