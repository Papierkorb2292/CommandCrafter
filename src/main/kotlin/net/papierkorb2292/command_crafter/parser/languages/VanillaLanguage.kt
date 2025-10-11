package net.papierkorb2292.command_crafter.parser.languages

import com.mojang.brigadier.ImmutableStringReader
import com.mojang.brigadier.ParseResults
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.context.*
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import com.mojang.datafixers.util.Either
import com.mojang.serialization.Codec
import com.mojang.serialization.Decoder
import com.mojang.serialization.JsonOps
import net.minecraft.command.CommandSource
import net.minecraft.command.MacroInvocation
import net.minecraft.command.SingleCommandAction
import net.minecraft.command.argument.CommandFunctionArgumentType
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtEnd
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.StringNbtReader
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryWrapper
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.registry.entry.RegistryEntryList
import net.minecraft.registry.tag.TagEntry
import net.minecraft.registry.tag.TagKey
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.function.FunctionBuilder
import net.minecraft.server.function.Macro
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.StringIdentifiable
import net.minecraft.util.Util
import net.minecraft.util.math.MathHelper
import net.minecraft.util.packrat.*
import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.editor.debugger.helper.plus
import net.papierkorb2292.command_crafter.editor.debugger.helper.withExtension
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.BreakpointCondition
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.BreakpointConditionParser
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugInformation
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionElementDebugInformation
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags.FunctionTagDebugHandler
import net.papierkorb2292.command_crafter.editor.processing.*
import net.papierkorb2292.command_crafter.editor.processing.helper.*
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult.RangedDataProvider
import net.papierkorb2292.command_crafter.editor.processing.partial_id_autocomplete.CompletionItemsPartialIdGenerator
import net.papierkorb2292.command_crafter.helper.*
import net.papierkorb2292.command_crafter.parser.*
import net.papierkorb2292.command_crafter.parser.helper.*
import org.eclipse.lsp4j.*
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Predicate
import java.util.stream.Collectors
import kotlin.math.max
import kotlin.math.min
import net.papierkorb2292.command_crafter.mixin.editor.debugger.FunctionBuilderAccessor as FunctionBuilderAccessor_Debug
import net.papierkorb2292.command_crafter.mixin.parser.FunctionBuilderAccessor as FunctionBuilderAccessor_Parser
import org.eclipse.lsp4j.jsonrpc.messages.Either as JsonRPCEither

data class VanillaLanguage(val easyNewLine: Boolean = false, val inlineResources: Boolean = false) : Language {
    override fun parseToVanilla(
        reader: DirectiveStringReader<RawZipResourceCreator>,
        source: ServerCommandSource,
        resource: RawResource,
    ) {
        while(skipToNextCommand(reader)) {
            if (LanguageManager.readDocComment(reader) != null)
                continue
            throwIfSlashPrefix(reader, reader.currentLine)
            if(reader.canRead() && reader.peek() == '$') {
                val macro = readMacro(reader)
                //For validation
                FunctionBuilderAccessor_Parser.init<ServerCommandSource>().addMacroCommand(
                    macro, reader.currentLine, source
                )

                resource.content += Either.left("$${macro}\n")
            } else {
                if(!easyNewLine)
                    reader.convertInputToEscapedMultiline()
                val parsed = parseCommand(reader, source)
                reader.disableEscapedMultiline()
                val string = parsed.reader.string
                val contextChain = ContextChain.tryFlatten(parsed.context.build(string))
                if(contextChain.isEmpty) {
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parsed.reader)
                }
                writeCommand(parsed, resource, reader)
            }
            if(reader.canRead(0) && reader.peek(-1) != '\n') {
                reader.checkEndLanguage()
                return
            }
        }
    }

    override fun analyze(
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        source: CommandSource,
        result: AnalyzingResult,
    ) {
        fun advanceToParseResults(parseResults: ParseResults<*>, reader: DirectiveStringReader<*>) {
            parseResults.reader.run {
                if(this is DirectiveStringReader<*>) {
                    reader.copyFrom(this)
                    if(this !== reader)
                        toCompleted()
                }
            }
        }

        var previousTextWasCommand = false
        while(skipToNextCommandAndAnalyze(reader, result, source, !easyNewLine || !previousTextWasCommand)) {
            if(Thread.currentThread().isInterrupted)
                return
            if(LanguageManager.readAndAnalyzeDocComment(reader, result) != null) {
                previousTextWasCommand = false
                continue
            }
            previousTextWasCommand = true
            try {
                if (reader.canRead() && reader.peek() == '/') {
                    if (reader.canRead(2) && reader.peek(1) == '/') {
                        throw DOUBLE_SLASH_EXCEPTION.createWithContext(reader)
                    }
                    val position = AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.fileMappingInfo)
                    result.diagnostics += Diagnostic(
                        Range(position, position.advance()),
                        "Unknown or invalid command on line \"${position.line + 1}\" (Do not use a preceding forwards slash.)"
                    )
                    reader.skip()
                }
                if(reader.canRead() && reader.peek() == '$') {
                    readAndAnalyzeMacro(reader, source, result)
                    continue
                }
                //Let command start at cursor 0, so completions don't overlap with suggestRootNode
                reader.cutReadChars()

                if(!easyNewLine)
                    reader.convertInputToEscapedMultiline()
                val parseResults = reader.dispatcher.parse(reader, source)
                advanceToParseResults(parseResults, reader)
                if(!easyNewLine) {
                    // Add back trimmed chars so suggestions are placed correctly
                    reader.disableTrimmingFromEscapedMultiline()
                }
                analyzeParsedCommand(parseResults, result, reader)
                // Skip any spaces from disableTrimmingFromEscapedMultiline so they aren't interpreted as trailing data
                reader.skipSpaces()

                val exception = parseResults.exceptions.entries.maxByOrNull { it.value.cursor }
                if(exception != null)
                    throw exception.value
                if(easyNewLine) {
                    if (parseResults.context.range.isEmpty) {
                        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand()
                            .createWithContext(parseResults.reader)
                    }
                } else if (reader.canRead() && reader.peek() != '\n' && !reader.scopeStack.element().closure.endsClosure(reader)) {
                    if(parseResults.context.range.isEmpty)
                        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand()
                            .createWithContext(parseResults.reader)
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                        .createWithContext(parseResults.reader)
                }
                val string = parseResults.reader.string
                val contextChain = ContextChain.tryFlatten(parseResults.context.build(string))
                if(contextChain.isEmpty) {
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parseResults.reader)
                }

                if(easyNewLine) {
                    if(!reader.canRead() || reader.scopeStack.element().closure.endsClosure(reader)) {
                        // Analyze last whitespace and then loop should end
                        continue
                    }
                    if (reader.peek() != '\n') {
                        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(reader)
                    } else reader.skip()
                } else {
                    reader.disableEscapedMultiline()
                    if(!reader.canRead() || reader.scopeStack.element().closure.endsClosure(reader)) {
                        // Analyze last whitespace and then loop should end
                        continue
                    }
                    reader.skip()
                }
            } catch(e: Exception) {
                val exceptionCursor = if (e is CursorAwareException && e.`command_crafter$getCursor`() != -1) {
                    val cursor = e.`command_crafter$getCursor`()
                    reader.cursor = max(reader.cursor, cursor)
                    cursor
                } else {
                    reader.cursor
                }
                reader.disableEscapedMultiline()
                val startPosition =
                    AnalyzingResult.getPositionFromCursor(reader.cursorMapper.mapToSource(reader.readSkippingChars + exceptionCursor), reader.fileMappingInfo)
                result.diagnostics += Diagnostic(
                    Range(
                        startPosition,
                        Position(startPosition.line, reader.lines[startPosition.line].length)
                    ),
                    e.message ?: e.toString(),
                    DiagnosticSeverity.Error,
                    null
                )

                while (true) {
                    if(!reader.canRead()) {
                        reader.checkEndLanguage()
                        return
                    }
                    if (reader.read() == '\n')
                        break
                }
            }
        }
    }

    private fun readAndAnalyzeMacro(
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        source: CommandSource,
        result: AnalyzingResult,
    ) {
        val startCursor = reader.cursor
        val macro = readMacro(reader)

        // Get only the relevant lines for caching
        val absoluteStartOffset = reader.readCharacters + startCursor
        val startOffsetPosition = AnalyzingResult.getPositionFromCursor(absoluteStartOffset, reader.fileMappingInfo)
        val relevantLines = mutableListOf<String>()
        AnalyzingResult.getInlineRangesBetweenCursors(
            absoluteStartOffset,
            reader.absoluteCursor,
            reader.fileMappingInfo
        ) { line, cursor, length ->
            relevantLines += reader.lines[line].substring(cursor, cursor + length)
        }
        var fullResult = reader.resourceCreator.previousCache?.vanillaMacroCache[relevantLines]
        if(fullResult == null) {
            val startTime = Util.getMeasuringTimeNano()
            val macroInvocation = ALLOW_MALFORMED_MACRO.runWithValue(true) {
                MacroInvocation.parse(macro)
            }
            val macroVariableValues = macroInvocation.variables.map { "" }

            @Suppress("CAST_NEVER_SUCCEEDS")
            val resolvedMacroCursorMapper = (macroInvocation as MacroCursorMapperProvider)
                .`command_crafter$getCursorMapper`(macroVariableValues)
            for(i in 0 until resolvedMacroCursorMapper.sourceCursors.size)
                resolvedMacroCursorMapper.sourceCursors[i] += 1 // Because leading '$' is included in the relevant lines, but not in the macro string that got parsed

            // Build a new FileMappingInfo that only includes the lines with the macro such that the result can be cached regardless of other file content
            val macroSourceFileInfo = FileMappingInfo(
                relevantLines,
                OffsetProcessedInputCursorMapper(-absoluteStartOffset)
                    .combineWith(reader.fileMappingInfo.cursorMapper)
                    .combineWith(OffsetProcessedInputCursorMapper(-reader.readSkippingChars))
            )
            val variablesSemanticTokens = SemanticTokensBuilder(macroSourceFileInfo)
            variablesSemanticTokens.addMultiline(startCursor, 1, TokenType.MACRO, 0)
            val diagnostics = mutableListOf<Diagnostic>()
            for((i, variable) in macroInvocation.variables.withIndex()) {
                val variableStart = resolvedMacroCursorMapper.sourceCursors[i] + resolvedMacroCursorMapper.lengths[i]
                variablesSemanticTokens.addMultiline(variableStart, 2, TokenType.MACRO, 0)
                variablesSemanticTokens.addMultiline(variableStart + 2, variable.length, TokenType.ENUM, 0)
                val variableNameStart = variableStart + 2
                val variableNameEnd = variableNameStart + variable.length
                val hasClosingParentheses = macro.getOrNull(variableNameEnd - 1) == ')'
                if(hasClosingParentheses) {
                    variablesSemanticTokens.addMultiline(variableNameEnd, 1, TokenType.MACRO, 0)
                    // Only check for a valid name if the macro has closing parentheses, otherwise it might be including too many chars anyway
                    // that aren't actually intended to be part of the name
                    if(!MacroInvocation.isValidMacroName(variable)) {
                        diagnostics += Diagnostic(
                            Range(
                                AnalyzingResult.getPositionFromCursor(
                                    macroSourceFileInfo.cursorMapper.mapToSource(variableNameStart),
                                    macroSourceFileInfo
                                ),
                                AnalyzingResult.getPositionFromCursor(
                                    macroSourceFileInfo.cursorMapper.mapToSource(variableNameEnd),
                                    macroSourceFileInfo
                                )
                            ),
                            "Invalid macro variable name '$variable'"
                        )
                    }
                } else {
                    val endPosition = AnalyzingResult.getPositionFromCursor(
                        macroSourceFileInfo.cursorMapper.mapToSource(variableNameEnd),
                        macroSourceFileInfo
                    )
                    diagnostics += Diagnostic(
                        Range(endPosition, endPosition.advance()),
                        "Unterminated macro variable"
                    )
                }
            }

            if(macroInvocation.variables.isEmpty()) {
                diagnostics += Diagnostic(
                    Range(Position(0, 0), Position(0, 1)), // Mark '$'
                    "No variables in macro"
                )
            }

            val replacedMacro = macroInvocation.apply(macroVariableValues)
            // A macro variable is present at the beginning of every segment except for the first one
            val macroVariableLocations = resolvedMacroCursorMapper.targetCursors.copy()
            macroVariableLocations.remove(0)

            val macroMappingInfo = FileMappingInfo(
                relevantLines,
                macroSourceFileInfo.cursorMapper.combineWith(resolvedMacroCursorMapper)
            )
            val macroAnalyzingResult = AnalyzingResult(macroMappingInfo, Position())
            analyzeMacroCommand(
                DirectiveStringReader(
                    macroMappingInfo,
                    reader.dispatcher,
                    AnalyzingResourceCreator(
                        reader.resourceCreator.languageServer,
                        reader.resourceCreator.sourceFunctionUri
                    )
                ).apply {
                    // Only read the actual macro, don't consume any of the original lines (they are still necessary for correct file positions though)
                    toCompleted()
                    string = replacedMacro
                },
                source,
                macroAnalyzingResult,
                macroVariableLocations
            )

            macroAnalyzingResult.semanticTokens.overlay(listOf(variablesSemanticTokens).iterator())
            macroAnalyzingResult.diagnostics += diagnostics
            fullResult = macroAnalyzingResult
            val duration = (Util.getMeasuringTimeNano() - startTime) / 1000
            println("Took ${duration}µs to analyze macro: $macro")
        }
        reader.resourceCreator.newCache.vanillaMacroCache[relevantLines] = fullResult
        result.combineWith(fullResult.addOffset(result, startOffsetPosition, absoluteStartOffset))
    }

    override fun parseToCommands(
        reader: DirectiveStringReader<ParsedResourceCreator?>,
        source: ServerCommandSource,
        builder: FunctionBuilder<ServerCommandSource>,
    ): FunctionDebugInformation? {
        val elementBreakpointParsers = mutableListOf<FunctionElementDebugInformation.FunctionElementProcessor>()
        while(skipToNextCommand(reader)) {
            if (LanguageManager.readDocComment(reader) != null)
                continue
            throwIfSlashPrefix(reader, reader.currentLine)
            if(reader.canRead() && reader.peek() == '$') {
                val startCursor = reader.absoluteCursor
                val startSkippedCharacters = reader.skippedChars
                builder.addMacroCommand(
                    readMacro(reader),
                    reader.currentLine,
                    source
                )
                val endCursorWithoutNewLine = reader.absoluteCursor - if(easyNewLine && reader.canRead(0) && reader.peek(-1) == '\n') 1 else 0
                val macroLines = (builder as FunctionBuilderAccessor_Debug).macroLines
                @Suppress("UNCHECKED_CAST")
                elementBreakpointParsers += FunctionElementDebugInformation.MacroElementProcessor(
                    macroLines.size - 1,
                    StringRange.between(startCursor, endCursorWithoutNewLine),
                    macroLines.last() as Macro.VariableLine<ServerCommandSource>,
                    reader.cursorMapper,
                    startSkippedCharacters
                )
                if(reader.canRead(0) && reader.peek(-1) != '\n')
                    break
            }
            if(!easyNewLine)
                reader.convertInputToEscapedMultiline()
            val parsed = parseCommand(reader, source)
            if(!easyNewLine) {
                reader.disableEscapedMultiline()
                reader.skipWhitespace()
            }
            val string = parsed.reader.string
            val contextChain = ContextChain.tryFlatten(parsed.context.build(string)).orElseThrow {
                CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parsed.reader)
            }
            elementBreakpointParsers += FunctionElementDebugInformation.CommandContextElementProcessor(contextChain.topContext)
            builder.addAction(SingleCommandAction.Sourced(string, contextChain))
            if(reader.canRead(0) && reader.peek(-1) != '\n')
                break
        }
        return reader.resourceCreator?.run {
            @Suppress("UNCHECKED_CAST")
            FunctionElementDebugInformation(
                elementBreakpointParsers,
                reader as DirectiveStringReader<ParsedResourceCreator>,
                VanillaBreakpointConditionParser,
                functionId.withExtension(".mcfunction")
            ).also { debugInformation ->
                originResourceInfoSetEventStack.peek().invoke {
                    debugInformation.functionId = it.id
                    debugInformation.setFunctionStringRange(it.range)
                }
            }
        }
    }

    private fun <S: CommandSource> parseCommand(reader: DirectiveStringReader<*>, source: S): ParseResults<S> {
        try {
            @Suppress("UNCHECKED_CAST")
            val parseResults: ParseResults<S> = reader.dispatcher.parse(reader, source) as ParseResults<S>
            val exceptions = parseResults.exceptions
            if (exceptions.isNotEmpty()) {
                throw exceptions.values.first()
            }
            parseResults.reader.run {
                if (this is DirectiveStringReader<*>) {
                    reader.copyFrom(this)
                    toCompleted()
                }
            }
            if(easyNewLine) {
                if (parseResults.context.range.isEmpty) {
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand()
                        .createWithContext(parseResults.reader)
                }
                reader.skipSpaces()
                if (reader.canRead() && reader.peek() != '\n') {
                    if (!reader.scopeStack.element().closure.endsClosure(reader))
                        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(reader)
                } else reader.skip()
            } else {
                if (parseResults.reader.canRead()) {
                    throw CommandManager.getException(parseResults)!!
                }
            }
            return parseResults
        } catch (commandSyntaxException: CommandSyntaxException) {
            throw IllegalArgumentException("Whilst parsing command on line ${reader.currentLine}: ${commandSyntaxException.message}")
        }
    }

    private fun throwIfSlashPrefix(reader: StringReader, line: Int) {
        if (reader.peek() == '/') {
            reader.skip()
            require(reader.peek() != '/') { "Unknown or invalid command on line $line (if you intended to make a comment, use '#' not '//')" }
            val string2: String = reader.readUnquotedString()
            throw IllegalArgumentException("Unknown or invalid command on line $line (did you mean '$string2'? Do not use a preceding forwards slash.)")
        }
    }

    private fun readMacro(reader: DirectiveStringReader<*>): String {
        if(!reader.canRead()) return ""
        if(!easyNewLine) {
            reader.convertInputToEscapedMultiline()
            reader.peek()
            // Add back trailing whitespace for analyzing (suggestions might use them)
            if(reader.resourceCreator is AnalyzingResourceCreator)
                reader.disableTrimmingFromEscapedMultiline()
            val macro = reader.readLine()
            reader.disableEscapedMultiline()
            return if(macro.startsWith('$')) macro.substring(1) else macro
        }
        val lineStart = reader.cursor
        val lineReadCharacters = reader.readCharacters
        val lineSkippedChars = reader.skippedChars
        if(reader.peek() == '$')
            reader.skip()
        val macroBuilder = StringBuilder(reader.readLine())
        reader.cursorMapper.addMapping(lineStart + lineReadCharacters, lineStart + lineReadCharacters - lineSkippedChars, reader.cursor - lineStart)
        var indentStartCursor = reader.cursor
        while(reader.tryReadIndentation { it > reader.currentIndentation }) {
            val skippedChars = reader.cursor - indentStartCursor // Note that skippedChars doesn't include newline characters. By not skipping this char, the mapping accounts for the additional ' ' characters.
            reader.string = reader.string.substring(0, indentStartCursor - 1) + ' ' + reader.string.substring(reader.cursor) //Also removes newline
            reader.cursor = indentStartCursor
            reader.skippedChars += skippedChars
            reader.readCharacters += skippedChars
            macroBuilder.append(' ')
            val lineStart = reader.cursor
            val lineReadCharacters = reader.readCharacters
            val lineSkippedChars = reader.skippedChars
            macroBuilder.append(reader.readLine())
            reader.cursorMapper.addMapping(lineStart + lineReadCharacters, lineStart + lineReadCharacters - lineSkippedChars, reader.cursor - lineStart)
            indentStartCursor = reader.cursor
        }
        return macroBuilder.toString()
    }

    private fun skipToNextCommand(reader: DirectiveStringReader<*>): Boolean {
        do {
            reader.cutReadChars()
            reader.saveIndentation()
            while(reader.canRead() && reader.peek() == '\n') {
                reader.skip()
                reader.saveIndentation()
            }
        } while(reader.endStatement() && reader.canRead() && reader.currentLanguage == this)
        return reader.canRead() && reader.currentLanguage == this
    }

    private fun skipToNextCommandAndAnalyze(reader: DirectiveStringReader<AnalyzingResourceCreator>, result: AnalyzingResult, source: CommandSource, ignorePrevIndent: Boolean): Boolean {
        if(reader.cursor != 0 && reader.peek(-1) != '\n') {
            if(!reader.canRead()) {
                reader.checkEndLanguage()
                return false
            }
            reader.readLine()
        }
        var readDirective = false
        val prevCommandIndent = reader.currentIndentation
        while(true) {
            reader.cutReadChars()
            reader.saveIndentation()
            val whitespaceEnd = reader.cursor
            // Reset cursor such that endStatementAndAnalyze can add correct completions and doesn't cut away the whitespace
            reader.cursor = 0
            val suggestEnd = if(readDirective || ignorePrevIndent) whitespaceEnd else min(whitespaceEnd, prevCommandIndent)
            suggestRootNode(
                reader,
                StringRange(0, suggestEnd),
                source,
                result
            )
            val readNewDirective = reader.endStatementAndAnalyze(result, false)
            readDirective = readNewDirective || readDirective
            // Skip whitespace again if endStatement didn't read anything
            reader.cursor = max(reader.cursor, whitespaceEnd)
            if(!reader.canRead() || reader.currentLanguage != this)
                return false
            if(readNewDirective)
                // '\n' is already skipped
                continue
            if(reader.peek() != '\n')
                return true
            reader.skip()
        }
    }

    fun writeCommand(result: ParseResults<ServerCommandSource>, resource: RawResource, reader: DirectiveStringReader<RawZipResourceCreator>) {
        var contextBuilder = result.context
        var context = contextBuilder.build(result.reader.string)
        var addLeadingSpace = false
        val stringBuilder = StringBuilder()
        while(contextBuilder != null && context != null) {
            for (parsedNode in contextBuilder.nodes) {
                if (addLeadingSpace) {
                    stringBuilder.append(' ')
                } else {
                    addLeadingSpace = true
                }
                val node = parsedNode.node
                if (node is StringifiableCommandNode) {
                    for(part in node.`command_crafter$stringifyNode`(
                        context,
                        parsedNode.range,
                        DirectiveStringReader(
                            FileMappingInfo(listOf(StringifiableCommandNode.stringifyNodeFromStringRange(context, parsedNode.range))),
                            reader.dispatcher,
                            reader.resourceCreator
                        ).apply {
                            val scope = reader.scopeStack.peek()
                            scopeStack.addFirst(scope)
                            currentLanguage = scope.language
                        }
                    )) {
                        part.ifLeft {
                            stringBuilder.append(it)
                        }.ifRight {
                            resource.content += Either.left(stringBuilder.toString())
                            stringBuilder.clear()
                            resource.content += Either.right(it)
                        }
                    }
                } else {
                    stringBuilder.append(StringifiableCommandNode.stringifyNodeFromStringRange(context, parsedNode.range))
                }
            }
            contextBuilder = contextBuilder.child
            context = context.child
        }
        resource.content += Either.left(stringBuilder.append('\n').toString())
    }

    fun <S> getAnalyzingParsedRootNode(rootNode: CommandNode<S>, completionStart: Int): ParsedCommandNode<S> {
        // Subtract 1 from position, because one character will be skipped between nodes such that the actual next node starts at completionStart
        return ParsedCommandNode(rootNode, StringRange.at(completionStart - 1))
    }

    fun suggestRootNode(reader: DirectiveStringReader<AnalyzingResourceCreator>, range: StringRange, commandSource: CommandSource, analyzingResult: AnalyzingResult) {
        reader.directiveManager.suggestDirectives(range, analyzingResult)
        val parsedRootNode = getAnalyzingParsedRootNode(reader.dispatcher.root, range.start)
        addNodeSuggestions(
            parsedRootNode,
            analyzingResult,
            // Use max value as start, such that the completion start is always at the cursor instead of the start of the range (addNodeSuggestions uses `min` to get the completion start)
            StringRange(Integer.MAX_VALUE, range.end),
            reader.copy().apply {
                // The string must be empty, so no matter where in the range completions are requested, the literals will be suggested
                string = ""
            },
            CommandContextBuilder(
                reader.dispatcher,
                commandSource,
                parsedRootNode.node,
                parsedRootNode.range.start
            ),
            false,
            completionsChannel = AnalyzingResult.LANGUAGE_COMPLETION_CHANNEL
        )
    }

    fun analyzeParsedCommand(
        result: ParseResults<CommandSource>,
        analyzingResult: AnalyzingResult,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        firstContextSkipNodesAmount: Int = 0
    ): CommandAnalyzingFootprint {
        var skipNodesAmount = firstContextSkipNodesAmount
        var contextBuilder = result.context
        var parentNode = getAnalyzingParsedRootNode(contextBuilder.rootNode, contextBuilder.range.start)
        while(contextBuilder != null) {
            for(parsedNode in contextBuilder.nodes) {
                if(skipNodesAmount == 0) {
                    analyzeCommandNode(
                        parsedNode,
                        parentNode,
                        contextBuilder,
                        analyzingResult,
                        reader
                    )
                } else {
                    --skipNodesAmount
                }
                parentNode = parsedNode
            }
            contextBuilder = contextBuilder.child
        }
        val nextNode = tryAnalyzeNextNode(
            analyzingResult,
            parentNode,
            result.context.lastChild,
            reader
        )
        return CommandAnalyzingFootprint(nextNode)
    }

    // Add root suggestions at the start of new lines for easyNewLine commands,
    // since at the start of the line there wouldn't be enough indentation to continue the previous command, so a new command can start there
    private fun addRootSuggestionsForImprovedCommandGap(
        gapRange: StringRange,
        analyzingResult: AnalyzingResult,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        commandSource: CommandSource,
    ) {
        if(!isReaderEasyNextLine(reader))
            // There can't be any improved command gaps
            return
        val gapReader = reader.copy()
        gapReader.cursor = gapRange.start
        while(gapReader.canRead() && gapReader.peek() != '\n')
            gapReader.skip()
        gapReader.skip() // Skip \n
        if(gapReader.cursor > gapRange.end) {
            // The gap doesn't span multiple lines
            return
        }
        do {
            val lineStart = gapReader.cursor
            while(gapReader.canRead() && gapReader.peek() != '\n')
                gapReader.skip()
            gapReader.skip() // Skip \n
            val lineEnd = gapReader.cursor
            val indentEnd = min(lineEnd, lineStart + gapReader.currentIndentation)
            suggestRootNode(gapReader, StringRange(lineStart, indentEnd), commandSource, analyzingResult)
        } while(gapReader.cursor <= gapRange.end)
    }

    private fun analyzeCommandNode(
        parsedNode: ParsedCommandNode<CommandSource>,
        parentNode: ParsedCommandNode<CommandSource>,
        contextBuilder: CommandContextBuilder<CommandSource>,
        analyzingResult: AnalyzingResult,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        skipAnalyzedChars: Boolean = false,
    ) {
        val initialReadCharacters = reader.readCharacters
        val initialSkippedChars = reader.skippedChars
        val commandInput = reader.string
        val context = contextBuilder.build(commandInput)
        val node = parsedNode.node
        val rootSuggestionsResult = analyzingResult.copyInput()
        addRootSuggestionsForImprovedCommandGap(
            StringRange(max(parentNode.range.end, 0), parsedNode.range.start),
            rootSuggestionsResult,
            reader,
            context.source
        )
        if (node is AnalyzingCommandNode) {
            // Modify the mapping info of the original reader, not just the analyzeReader, because the original mapping info is still used in the AnalyzingResult
            reader.readCharacters = (parsedNode as CursorOffsetContainer).`command_crafter$getReadCharacters`()
            reader.skippedChars = (parsedNode as CursorOffsetContainer).`command_crafter$getSkippedChars`()
            val analyzeReader = reader.copy()
            analyzeReader.cursor = parsedNode.range.start
            try {
                val nodeAnalyzingResult = analyzingResult.copyInput()
                try {
                    node.`command_crafter$analyze`(
                        context,
                        StringRange(
                            parsedNode.range.start,
                            MathHelper.clamp(parsedNode.range.end, parsedNode.range.start, context.input.length)
                        ),
                        analyzeReader,
                        nodeAnalyzingResult,
                        node.name
                    )
                } catch(e: Exception) {
                    CommandCrafter.LOGGER.debug("Error while analyzing command node ${node.name}", e)
                }
                if(skipAnalyzedChars) {
                    // Choose maximum because the analyzer might not have an implementation that reads anything
                    reader.cursor = max(reader.cursor, analyzeReader.cursor)
                    reader.furthestAccessedCursor = max(reader.furthestAccessedCursor, analyzeReader.furthestAccessedCursor)
                }
                if(node !is CustomCompletionsCommandNode || !node.`command_crafter$hasCustomCompletions`(
                        context,
                        node.name
                    )
                ) {
                    analyzingResult.combineWithExceptCompletions(nodeAnalyzingResult)
                    addNodeSuggestions(
                        parentNode,
                        analyzingResult,
                        parsedNode.range,
                        analyzeReader,
                        contextBuilder,
                        !easyNewLine,
                        nodeAnalyzingResult,
                        rootSuggestionsResult
                    )
                } else {
                    analyzingResult.combineWith(nodeAnalyzingResult)
                }
            } finally {
                reader.readCharacters = initialReadCharacters
                reader.skippedChars = initialSkippedChars
            }
        } else {
            analyzingResult.combineWithCompletionProviders(rootSuggestionsResult)
        }
    }

    private fun addNodeSuggestions(
        parentNode: ParsedCommandNode<CommandSource>,
        analyzingResult: AnalyzingResult,
        parsedNodeRange: StringRange,
        completionReader: DirectiveStringReader<AnalyzingResourceCreator>,
        contextBuilder: CommandContextBuilder<CommandSource>,
        clampInCursorMapperGaps: Boolean,
        additionalCompletions: AnalyzingResult? = null,
        rootCompletions: AnalyzingResult? = null,
        completionsChannel: String = AnalyzingResult.LANGUAGE_COMPLETION_CHANNEL,
    ) {
        val completionParentNode = parentNode.node.resolveRedirects()
        analyzingResult.addCompletionProviderWithContinuosMapping(
            completionsChannel,
            AnalyzingResult.RangedDataProvider(
                StringRange(
                    parentNode.range.end + 1,
                    parsedNodeRange.end
                )
            ) { sourceCursor ->
                val rootCompletionProvider = rootCompletions?.getCompletionProviderForCursor(sourceCursor)
                if(rootCompletionProvider != null)
                    return@RangedDataProvider rootCompletionProvider.dataProvider(sourceCursor)

                val lineNumber = AnalyzingResult.getPositionFromCursor(sourceCursor, completionReader.fileMappingInfo).line
                val targetCursor = completionReader.cursorMapper.mapToTarget(sourceCursor, clampInCursorMapperGaps)
                val endCursor = targetCursor - completionReader.readSkippingChars
                val truncatedInput = completionReader.string
                    .substring(0, min(endCursor, completionReader.string.length))
                // The string is extended to a length that covers the cursor (only happens for root suggestions, otherwise the cursor is already contained),
                // so the suggestions are at the correct location
                val extendedTruncatedInput = " ".repeat(max(endCursor - truncatedInput.length, 0)) + truncatedInput
                val truncatedInputLowerCase = extendedTruncatedInput.lowercase(Locale.ROOT)
                SUGGESTIONS_FULL_INPUT.set(completionReader.copy().apply {
                    this.cursor = endCursor
                })
                val suggestionFutures = completionParentNode.children.map { child ->
                    try {
                        child.listSuggestions(
                            contextBuilder.build(extendedTruncatedInput),
                            SuggestionsBuilder(
                                extendedTruncatedInput, truncatedInputLowerCase,
                                min(parsedNodeRange.start, extendedTruncatedInput.length)
                            )
                        )
                    } catch(e: Exception) {
                        CommandCrafter.LOGGER.debug("Error while getting suggestions for command node ${child.name}", e)
                        Suggestions.empty()
                    }
                }.toTypedArray()
                val commandCompletionsFuture = CompletableFuture.allOf(*suggestionFutures).exceptionallyCompose {
                    SUGGESTIONS_FULL_INPUT.remove()
                    CompletableFuture.failedFuture(it)
                }.thenApply {
                    SUGGESTIONS_FULL_INPUT.remove()
                    val completionItems = suggestionFutures.flatMap { it.get().list }.toSet().map {
                        it.toCompletionItem(completionReader, lineNumber, sourceCursor)
                    } + suggestionFutures.flatMap {
                        (it.get() as CompletionItemsContainer).`command_crafter$getCompletionItems`()
                            ?: emptyList()
                    }
                    if(completionReader.resourceCreator.languageServer != null) {
                        // Partial Completions are added only on the side with the language server, so they aren't added twice
                        CompletionItemsPartialIdGenerator.addPartialIdsToCompletionItems(
                            completionItems,
                            completionReader.string.substring(
                                min(
                                    parsedNodeRange.start,
                                    completionReader.string.length
                                )
                            )
                        )
                    } else completionItems
                }
                if(additionalCompletions == null)
                    return@RangedDataProvider commandCompletionsFuture
                val additionalCompletionsProvider = additionalCompletions.getCompletionProviderForCursor(sourceCursor)
                    ?: return@RangedDataProvider commandCompletionsFuture
                commandCompletionsFuture.thenCombine(
                    additionalCompletionsProvider.dataProvider(sourceCursor)
                ) { commandCompletions, additionalCompletions ->
                    commandCompletions + additionalCompletions
                }
            }
        )
    }

    private fun tryAnalyzeNextNode(analyzingResult: AnalyzingResult, parentNode: ParsedCommandNode<CommandSource>, context: CommandContextBuilder<CommandSource>, reader: DirectiveStringReader<AnalyzingResourceCreator>): CommandNode<CommandSource>? {
        val initialCursor = reader.cursor
        if(isReaderEasyNextLine(reader)) {
            // Don't skip more if a whitespace was already skipped, because the command parser won't skip both
            if(reader.cursor == 0 || (reader.canRead(0) && reader.peek(-1) != ' ')) {
                if(reader.canRead() && reader.peek() == ' ')
                    reader.skip()
                else {
                    var lastLineEnd = reader.cursor
                    while(reader.canRead()) {
                        if(reader.peek() != '\n') {
                            reader.cursor = lastLineEnd
                            break
                        }
                        lastLineEnd = reader.cursor
                        reader.skip()
                        reader.skipSpaces()
                    }
                }
            }
        } else if(reader.canRead() && reader.peek() == ' ' && reader.cursor > 0 && reader.peek(-1) != ' ')
            reader.skip()

        var furthestParsedReader: DirectiveStringReader<AnalyzingResourceCreator>? = null
        var furthestParsedContext: CommandContextBuilder<CommandSource>? = null
        for(nextNode in parentNode.node.resolveRedirects().children) {
            val newReader = reader.copy()
            val start = newReader.cursor
            val newContext = context.copy()
            when(nextNode) {
                is LiteralCommandNode<*> -> {
                    var literalIndex = 0
                    while(newReader.canRead() && literalIndex < nextNode.literal.length && newReader.peek() == nextNode.literal[literalIndex]) {
                        literalIndex++
                        newReader.skip()
                    }
                }
                is ArgumentCommandNode<*, *> -> {
                    try {
                        val argument = nextNode.type.parse(newReader)
                        newContext.withArgument(nextNode.name, ParsedArgument(start, newReader.cursor, argument))
                    } catch(_: Exception) { }
                }
                else -> continue
            }
            newContext.withNode(nextNode, StringRange(start, reader.nextLineEnd))
            if(furthestParsedReader == null || newReader.cursor > furthestParsedReader.cursor) {
                furthestParsedReader = newReader
                furthestParsedContext = newContext
            }
        }
        if(
            furthestParsedContext == null
            || furthestParsedReader == null
            || furthestParsedContext.nodes.last().range <= parentNode.range
            ) {
            reader.cursor = initialCursor
            return null
        }
        analyzeCommandNode(
            furthestParsedContext.nodes.last(),
            parentNode,
            furthestParsedContext,
            analyzingResult,
            furthestParsedReader,
            true // Ensure that the next command only starts after the end of this argument, so their AnalyzingResult contents don't intersect
        )
        reader.copyFrom(furthestParsedReader)
        return furthestParsedContext.nodes.last().node
    }

    data class CommandAnalyzingFootprint(val triedNextNode: CommandNode<CommandSource>?)

    object VanillaLanguageType : LanguageManager.LanguageType {
        enum class VanillaLanguageOptions(val optionName: String) : StringIdentifiable {
            DEFAULT("default"),
            ALL_FEATURES("improved"),
            EASY_NEW_LINE("easyNewLine"),
            INLINE_RESOURCES("inlineResources");

            override fun asString() = optionName
        }
        
        override val argumentDecoder: Decoder<Language> = StringIdentifiable.createCodec(VanillaLanguageOptions.entries::toTypedArray)
            .orEmpty(VanillaLanguageOptions.DEFAULT)
            .map {
                when(it!!) {
                    VanillaLanguageOptions.DEFAULT -> VanillaLanguage(easyNewLine = false, inlineResources = false)
                    VanillaLanguageOptions.EASY_NEW_LINE -> VanillaLanguage(easyNewLine = true, inlineResources = false)
                    VanillaLanguageOptions.INLINE_RESOURCES -> VanillaLanguage(easyNewLine = false, inlineResources = true)
                    VanillaLanguageOptions.ALL_FEATURES -> VanillaLanguage(easyNewLine = true, inlineResources = true)
                }
            }
    }

    companion object {
        const val ID = "vanilla"

        val SUGGESTIONS_FULL_INPUT = ThreadLocal<DirectiveStringReader<AnalyzingResourceCreator>>()
        val ALLOW_MALFORMED_MACRO = ThreadLocal<Boolean>()
        val shouldDisplayWarningOnMacroTimeout = true //TODO: Turn off before release

        private val DOUBLE_SLASH_EXCEPTION = SimpleCommandExceptionType(Text.literal("Unknown or invalid command  (if you intended to make a comment, use '#' not '//')"))
        private val COMMAND_NEEDS_NEW_LINE_EXCEPTION = SimpleCommandExceptionType(Text.of("Command doesn't end with a new line"))

        //TODO: Error on trailing data
        fun analyzeMacroCommand(reader: DirectiveStringReader<AnalyzingResourceCreator>, source: CommandSource, baseAnalyzingResult: AnalyzingResult, macroVariableLocations: IntList) {
            reader.enterClosure(Language.TopLevelClosure(VanillaLanguage()))

            val crawlerRunner = MacroAnalyzingCrawlerRunner(
                CommandContextBuilder(reader.dispatcher, source, reader.dispatcher.root, reader.cursor),
                reader,
                macroVariableLocations,
                baseAnalyzingResult
            )
            val analyzingResult = crawlerRunner.run()

            // TODO: Have some warnings when the command appears to be wrong
            // Remove all errors for now, because no proper error handling is implemented yet
            analyzingResult.diagnostics.clear()

            if(crawlerRunner.hasHitTimeout && shouldDisplayWarningOnMacroTimeout)
                analyzingResult.diagnostics += Diagnostic().apply {
                    message = "Macro took unexpectedly long to analyze"
                    range = Range(
                        AnalyzingResult.getPositionFromCursor(baseAnalyzingResult.mappingInfo.cursorMapper.mapToSource(0), baseAnalyzingResult.mappingInfo),
                        AnalyzingResult.getPositionFromCursor(baseAnalyzingResult.mappingInfo.cursorMapper.mapToSource(reader.string.length), baseAnalyzingResult.mappingInfo)
                    )
                    severity = DiagnosticSeverity.Hint
                }

            baseAnalyzingResult.addCompletionProviderWithContinuosMapping(
                AnalyzingResult.LANGUAGE_COMPLETION_CHANNEL,
                RangedDataProvider(StringRange(0, reader.string.length)) { sourceCursor: Int ->
                    val completionProvider = analyzingResult.getCompletionProviderForCursor(sourceCursor)
                    if(completionProvider == null) return@RangedDataProvider CompletableFuture.completedFuture(mutableListOf())
                    val completionFuture = completionProvider.dataProvider.invoke(sourceCursor)
                    completionFuture.thenApply {
                        it.distinct()
                    }
                }
            )
            baseAnalyzingResult.combineWithExceptCompletions(analyzingResult)
        }

        fun skipComments(reader: DirectiveStringReader<*>): Boolean {
            var foundAny = false
            while(true) {
                if(!reader.trySkipWhitespace {
                        if(reader.canRead() && reader.peek() == '#') {
                            @Suppress("ControlFlowWithEmptyBody")
                            while (reader.canRead() && reader.read() != '\n') { }
                            return@trySkipWhitespace true
                        }
                        false
                    }) {
                    return foundAny
                }
                foundAny = true
            }
        }

        fun isReaderVanilla(reader: ImmutableStringReader): Boolean {
            return reader is DirectiveStringReader<*> && reader.currentLanguage is VanillaLanguage
        }

        fun isReaderEasyNextLine(reader: ImmutableStringReader): Boolean {
            return reader is DirectiveStringReader<*> && reader.currentLanguage is VanillaLanguage && (reader.currentLanguage as VanillaLanguage).easyNewLine
        }

        fun isReaderInlineResources(reader: ImmutableStringReader): Boolean {
            return reader is DirectiveStringReader<*> && reader.currentLanguage is VanillaLanguage && (reader.currentLanguage as VanillaLanguage).inlineResources
        }

        private val tagEntryListCodec = TagEntry.CODEC.listOf()
        private val analyzeFunctionReferenceCodec = Codec.either(
            tagEntryListCodec,
            Codec.either(
                // Unit to suggest {} for inline functions
                Codec.unit(Unit),
                // Suggesting 'this'
                StringIdentifiable.createCodec({ arrayOf(StringIdentifiableUnit.INSTANCE) }, { "this" })
            )
        )

        fun <T> parseRawRegistryTagTuple(
            reader: DirectiveStringReader<RawZipResourceCreator>,
            registry: RegistryWrapper.Impl<T>,
        ): RawResourceRegistryEntryList<T> =
            RawResourceRegistryEntryList(parseRawTagTupleEntries(
                reader,
                RawResource.RawResourceType(RegistryKeys.getTagPath(registry.key), "json")
            ))

        private val missingInlineTagReferencesException = Dynamic2CommandExceptionType { sourceFunction: Any, missing: Any ->
            Text.of("Couldn't load function $sourceFunction, as a registry tag created by it is missing following references: $missing")
        }

        fun <T> parseParsedRegistryTagTuple(
            reader: DirectiveStringReader<ParsedResourceCreator>,
            registry: RegistryWrapper.Impl<T>,
        ): RegistryEntryList.ListBacked<T> {
            val entries = parseTagTupleEntries(reader)
            @Suppress("UNCHECKED_CAST")
            val registryKey = registry.key as RegistryKey<out Registry<T>>
            return object : RegistryEntryList.ListBacked<T>() {
                override fun isBound(): Boolean {
                    val checkBoundValueGetter = object : TagEntry.ValueGetter<Any> {
                        override fun direct(id: Identifier, required: Boolean) = Unit

                        override fun tag(id: Identifier): Collection<Any>? =
                                if(registry.getOptional(TagKey.of(registryKey, id)).isPresent) emptyList() else null
                    }
                    for(entry in entries)
                        if(!entry.resolve(checkBoundValueGetter) {})
                            return false
                    return true
                }

                val resolvedEntries by lazy {
                    val result: MutableList<RegistryEntry<T>> = ArrayList()
                    val valueGetter = object : TagEntry.ValueGetter<RegistryEntry<T>> {
                        override fun direct(id: Identifier, required: Boolean): RegistryEntry<T>
                                = registry.getOrThrow(RegistryKey.of(registryKey, id))

                        override fun tag(id: Identifier): Collection<RegistryEntry<T>>
                                = registry.getOrThrow(TagKey.of(registryKey, id)).toList()
                    }
                    val missingReferences: MutableList<TagEntry> = ArrayList()
                    for(entry in entries)
                        if(!entry.resolve(valueGetter, result::add))
                            missingReferences += entry
                    if(missingReferences.isNotEmpty()) {
                        throw missingInlineTagReferencesException.create(
                            reader.resourceCreator.functionId,
                            missingReferences.stream()
                                .map(Objects::toString)
                                .collect(Collectors.joining(", "))
                        )
                    }
                    result
                }
                val entrySet by lazy {
                    resolvedEntries.toSet()
                }

                override fun getStorage(): Either<TagKey<T>, MutableList<RegistryEntry<T>>>
                        = Either.right(resolvedEntries)

                override fun getTagKey() = Optional.empty<TagKey<T>>()

                override fun getEntries() = resolvedEntries

                override fun contains(entry: RegistryEntry<T>?) = entrySet.contains(entry)
            }
        }

        fun <T> analyzeRegistryTagTuple(
            reader: DirectiveStringReader<AnalyzingResourceCreator>,
            registry: RegistryWrapper.Impl<T>,
            throwSyntaxErrors: Boolean = true,
            hasNegationChar: Boolean = false,
            suggestNegationChar: Boolean = false,
        ): AnalyzedRegistryEntryList<T> {
            val analyzingResult = AnalyzingResult(reader.fileMappingInfo, AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.fileMappingInfo))
            val codec = if(suggestNegationChar) Codec.either(
                tagEntryListCodec,
                // Also suggest inverted lists
                StringIdentifiable.createCodec({ arrayOf(StringIdentifiableUnit.INSTANCE) }, { "![]" })
            ) else tagEntryListCodec
            StringRangeTreeJsonResourceAnalyzer.CURRENT_TAG_ANALYZING_REGISTRY.runWithValue(registry) {
                analyzeTagTupleEntries(reader, analyzingResult, codec, throwSyntaxErrors, hasNegationChar)
            }
            return AnalyzedRegistryEntryList(analyzingResult)
        }

        fun <T> parseRegistryTagTuple(
            reader: DirectiveStringReader<*>,
            registry: RegistryWrapper.Impl<T>,
        ): RegistryEntryList<T> {
            reader.withNoMultilineRestriction<Nothing> {
                it.resourceCreator.run {
                    if(this is RawZipResourceCreator) {
                        @Suppress("UNCHECKED_CAST") //It's not, it was checked in the previous 'if' statement
                        return parseRawRegistryTagTuple(it as DirectiveStringReader<RawZipResourceCreator>, registry)
                    }
                    if(this is ParsedResourceCreator) {
                        @Suppress("UNCHECKED_CAST") //It's not, it was checked in the previous 'if' statement
                        return parseParsedRegistryTagTuple(it as DirectiveStringReader<ParsedResourceCreator>, registry)
                    }
                    if(this is AnalyzingResourceCreator) {
                        @Suppress("UNCHECKED_CAST") //It's not, it was checked in the previous 'if' statement
                        return analyzeRegistryTagTuple(it as DirectiveStringReader<AnalyzingResourceCreator>, registry)
                    }
                    throw ParsedResourceCreator.RESOURCE_CREATOR_UNAVAILABLE_EXCEPTION.createWithContext(it)
                }
            }
        }

        private fun parseParsedInlineFunction(
            reader: DirectiveStringReader<ParsedResourceCreator>,
            source: ServerCommandSource,
            idSetter: (Identifier) -> Unit,
        ) {
            val startCursor = reader.absoluteCursor
            val functionIdSetter = ParsedResourceCreator.ResourceInfoSetterWrapper(reader.resourceCreator.addOriginResource())
            reader.expect('{')
            val function = LanguageManager.parseToCommands(reader, source, NestedVanillaClosure(reader.currentLanguage!!))

            reader.resourceCreator.functions += ParsedResourceCreator.AutomaticResource(functionIdSetter, function)
            functionIdSetter.range = StringRange(startCursor, reader.absoluteCursor)
            reader.resourceCreator.originResourceIdSetEventStack.element()(idSetter)
            reader.resourceCreator.popOriginResource()
        }

        private fun parseRawInlineFunction(reader: DirectiveStringReader<RawZipResourceCreator>, source: ServerCommandSource): RawResource {
            reader.expect('{')
            val resource = RawResource(RawResource.FUNCTION_TYPE)
            LanguageManager.parseToVanilla(reader, source, resource, NestedVanillaClosure(reader.currentLanguage!!))
            return resource
        }

        fun parseRawImprovedFunctionReference(reader: DirectiveStringReader<RawZipResourceCreator>, source: ServerCommandSource): RawResourceFunctionArgument? {
            if(!reader.canRead()) {
                return null
            }
            if(reader.canRead(4) && reader.string.startsWith("this", reader.cursor)) {
                reader.cursor += 4
                return RawResourceFunctionArgument(reader.resourceCreator.resourceStack.element())
            }
            if(reader.peek() == '{') {
                return RawResourceFunctionArgument(parseRawInlineFunction(reader, source))
            }
            if(reader.peek() != '[') {
                return null
            }
            return RawResourceFunctionArgument(parseRawTagTupleEntries(reader, RawResource.FUNCTION_TAG_TYPE), true)
        }

        fun parseParsedImprovedFunctionReference(reader: DirectiveStringReader<ParsedResourceCreator>, source: ServerCommandSource): MutableFunctionArgument? {
            if(!reader.canRead()) {
                return null
            }
            if(reader.canRead(4) && reader.string.startsWith("this", reader.cursor)) {
                reader.cursor += 4
                return MutableFunctionArgument(false).apply {
                    reader.resourceCreator.originResourceIdSetEventStack.element()(idSetter)
                }
            }
            if(reader.peek() == '{') {
                val result = MutableFunctionArgument(false)
                parseParsedInlineFunction(reader, source, result.idSetter)
                return result
            }
            if(reader.peek() != '[') {
                return null
            }
            val entries = parseTagTupleEntries(reader, true)
            val result = MutableFunctionArgument(true)
            reader.resourceCreator.functionTags += ParsedResourceCreator.AutomaticResource(result.idSetter, entries)
            return result
        }

        fun analyzeInlineFunction(
            reader: DirectiveStringReader<AnalyzingResourceCreator>,
            source: CommandSource,
            analyzingResult: AnalyzingResult,
        ) {
            reader.expect('{')
            LanguageManager.analyse(reader, source, analyzingResult, NestedVanillaClosure(reader.currentLanguage!!))
        }

        fun analyzeImprovedFunctionReference(
            reader: DirectiveStringReader<AnalyzingResourceCreator>,
            source: CommandSource,
            isAnalyzingParsedNode: Boolean = false,
        ): AnalyzedFunctionArgument? {
            val analyzingResult = AnalyzingResult(reader.fileMappingInfo, AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.fileMappingInfo))
            if(reader.canRead() && reader.peek() == '[') {
                analyzeTagTupleEntries(reader, analyzingResult, analyzeFunctionReferenceCodec, throwSyntaxErrors = !isAnalyzingParsedNode)
                return AnalyzedFunctionArgument(analyzingResult)
            }
            // Analyze inline tag even when it is not present to add suggestions
            val completionReaderCopy = reader.copy()
            completionReaderCopy.toCompleted()
            val inlineTagAnalyzingResult = analyzingResult.copyInput()
            analyzeTagTupleEntries(completionReaderCopy, inlineTagAnalyzingResult, analyzeFunctionReferenceCodec, throwSyntaxErrors = false)
            inlineTagAnalyzingResult.diagnostics.removeAll { it.severity == DiagnosticSeverity.Error }
            inlineTagAnalyzingResult.semanticTokens.clear()
            analyzingResult.combineWith(inlineTagAnalyzingResult)

            if(reader.canRead(4) && reader.string.startsWith("this", reader.cursor)) {
                analyzingResult.semanticTokens.addMultiline(reader.cursor, 4, TokenType.KEYWORD, 0)
                val resourceCreator = reader.resourceCreator
                val languageServer = resourceCreator.languageServer
                val functionAnalyzingResult = resourceCreator.resourceStack.element().analyzingResult
                val argRange = StringRange(reader.cursor, reader.cursor + 4)
                analyzingResult.addDefinitionProvider(AnalyzingResult.RangedDataProvider(argRange) {
                    return@RangedDataProvider CompletableFuture.completedFuture(
                        JsonRPCEither.forLeft(
                            listOf(Location(resourceCreator.sourceFunctionUri, Range(functionAnalyzingResult.filePosition, functionAnalyzingResult.filePosition)))
                        )
                    )
                }, true)
                if(languageServer != null) {
                    val fileRange = functionAnalyzingResult.toFileRange(argRange)
                    analyzingResult.addHoverProvider(AnalyzingResult.RangedDataProvider(argRange) {
                        return@RangedDataProvider languageServer.hoverDocumentation(functionAnalyzingResult, fileRange)
                    }, true)
                }
                reader.cursor += 4
            } else if(reader.canRead() && reader.peek() == '{') {
                analyzeInlineFunction(reader, source, analyzingResult)
            } else if(!isAnalyzingParsedNode) {
                return null
            }
            return AnalyzedFunctionArgument(analyzingResult)
        }

        fun parseImprovedFunctionReference(reader: DirectiveStringReader<*>, source: CommandSource): CommandFunctionArgumentType.FunctionArgument? {
            reader.withNoMultilineRestriction<Nothing> {
                it.resourceCreator.run {
                    if(this is RawZipResourceCreator && source is ServerCommandSource) {
                        @Suppress("UNCHECKED_CAST") //It's not, it was checked in the previous 'if' statement
                        return parseRawImprovedFunctionReference(it as DirectiveStringReader<RawZipResourceCreator>, source)
                    }
                    if(this is ParsedResourceCreator && source is ServerCommandSource) {
                        @Suppress("UNCHECKED_CAST") //It's not, it was checked in the previous 'if' statement
                        return parseParsedImprovedFunctionReference(it as DirectiveStringReader<ParsedResourceCreator>, source)
                    }
                    if(this is AnalyzingResourceCreator) {
                        @Suppress("UNCHECKED_CAST") //It's not, it was checked in the previous 'if' statement
                        return analyzeImprovedFunctionReference(it as DirectiveStringReader<AnalyzingResourceCreator>, source)
                    }
                    throw ParsedResourceCreator.RESOURCE_CREATOR_UNAVAILABLE_EXCEPTION.createWithContext(it)
                }
            }
        }

        private fun parseTagTupleEntries(
            reader: DirectiveStringReader<*>,
            saveFunctionTagRanges: Boolean = false,
        ): List<TagEntry> {
            val nbtReader = StringNbtReader.fromOps(NbtOps.INSTANCE)
            val treeBuilder = StringRangeTree.Builder<NbtElement>()
            if(saveFunctionTagRanges) {
                @Suppress("UNCHECKED_CAST", "KotlinConstantConditions")
                (nbtReader as StringRangeTreeCreator<NbtElement>).`command_crafter$setStringRangeTreeBuilder`(
                    treeBuilder
                )
            }
            val nbt = nbtReader.readAsArgument(reader)
            if(saveFunctionTagRanges) {
                val tree = treeBuilder.build(nbt)
                val absoluteTargetRanges = tree.ranges.mapValues { it.value + reader.readSkippingChars }
                FunctionTagDebugHandler.TAG_PARSING_ELEMENT_RANGES.runWithValue(absoluteTargetRanges) {
                    return tagEntryListCodec.decode(NbtOps.INSTANCE, nbt).orThrow.first
                }
            }
            return tagEntryListCodec.decode(NbtOps.INSTANCE, nbt).orThrow.first
        }

        private fun <ResourceCreator> parseRawTagTupleEntries(
            reader: DirectiveStringReader<ResourceCreator>,
            type: RawResource.RawResourceType,
        ): RawResource {
            val resource = RawResource(type)
            val tagEntries = parseTagTupleEntries(reader)
            val encodedJson = tagEntryListCodec.encode(tagEntries, JsonOps.INSTANCE, JsonOps.INSTANCE.empty()).orThrow
            resource.content += Either.left("{\"values\":${encodedJson}}")
            return resource
        }

        private fun <ResourceCreator> analyzeTagTupleEntries(
            reader: DirectiveStringReader<ResourceCreator>,
            analyzingResult: AnalyzingResult,
            decoder: Decoder<*>,
            throwSyntaxErrors: Boolean = true,
            hasNegationChar: Boolean = false,
        ) {
            val startCursor = reader.cursor
            // Copy reader when throwSyntaxErrors is true, such that the original reader can be used to parse without allowing malformed afterward
            val malformedReader = if(throwSyntaxErrors) reader.copy() else reader
            val nbtReader = StringNbtReader.fromOps(NbtOps.INSTANCE)
            val treeBuilder = StringRangeTree.Builder<NbtElement>()
            @Suppress("UNCHECKED_CAST", "KotlinConstantConditions")
            (nbtReader as StringRangeTreeCreator<NbtElement>).`command_crafter$setStringRangeTreeBuilder`(treeBuilder)
            (nbtReader as AllowMalformedContainer).`command_crafter$setAllowMalformed`(true)
            val nbt = try {
                nbtReader.readAsArgument(malformedReader)
            } catch(e: Exception) {
                treeBuilder.addNode(NbtEnd.INSTANCE, StringRange(startCursor, malformedReader.cursor), startCursor)
                NbtEnd.INSTANCE
            }
            var tree = treeBuilder.build(nbt)
            if(hasNegationChar) {
                val rootRange = tree.ranges[tree.root]!!
                treeBuilder.addNode(tree.root, StringRange(rootRange.start - 1, rootRange.end), rootRange.start - 1)
                tree = treeBuilder.build(nbt)
            }
            StringRangeTree.TreeOperations.forNbt(tree, malformedReader)
                // Don't escape 'this' string for function references, '![]' for registry tags and remove empty string completion
                .withSuggestionResolver(NbtSuggestionResolver(malformedReader) { it.value != "this" && it.value != "![]" && it.value.isNotEmpty() })
                .analyzeFull(analyzingResult, contentDecoder = decoder)

            if(throwSyntaxErrors) {
                // Read again without allowing malformed to get syntax errors
                StringNbtReader.fromOps(NbtOps.INSTANCE).readAsArgument(reader)
            }
        }

        fun skipImprovedCommandGap(reader: DirectiveStringReader<*>): Boolean {
            val cursor = reader.cursor
            reader.skipSpaces()
            if(!reader.canRead() || reader.peek() != '\n') {
                return true
            }
            var newLineIndentation = 0
            while(reader.canRead() && reader.peek() == '\n') {
                reader.skip()
                newLineIndentation = reader.readIndentation()
            }
            if(reader.canRead() && newLineIndentation > reader.currentIndentation) {
                return true
            }
            reader.cursor = cursor
            return false
        }
    }

    object VanillaBreakpointConditionParser : BreakpointConditionParser {
        override fun parseCondition(condition: String?, hitCondition: String?): BreakpointCondition {
            //TODO
            return object : BreakpointCondition {
                override fun checkCondition(source: ServerCommandSource): Boolean {
                    return true
                }

                override fun checkHitCondition(source: ServerCommandSource): Boolean {
                    return true
                }

            }
        }
    }

    class NestedVanillaClosure(override val startLanguage: Language) : Language.LanguageClosure {
        override fun endsClosure(reader: DirectiveStringReader<*>, skipNewLine: Boolean): Boolean {
            val startCursor = reader.cursor
            reader.skipWhitespace(skipNewLine)
            val result = reader.canRead() && reader.peek() == '}'
            reader.cursor = startCursor
            return result
        }

        override fun skipClosureEnd(reader: DirectiveStringReader<*>, skipNewLine: Boolean) {
            reader.skipWhitespace(skipNewLine)
            reader.skip()
        }
    }

    class InlineTagRule<Testee, TagEntry>(val registry: RegistryWrapper.Impl<TagEntry>, val testeeProjection: (Testee) -> RegistryEntry<TagEntry>) : ParsingRule<StringReader, Predicate<Testee>> {
        override fun parse(state: ParsingState<StringReader>): Predicate<Testee>? {
            val reader = state.reader
            val cursor = state.cursor
            if(!isReaderInlineResources(reader))
                return null
            val directiveReader = reader as DirectiveStringReader<*>
            try {
                if(directiveReader.resourceCreator is AnalyzingResourceCreator) {
                    val isInlineTag = directiveReader.canRead() && directiveReader.peek() == '['
                    val analyzingResult = PackratParserAdditionalArgs.analyzingResult.getOrNull()?.analyzingResult
                    @Suppress("UNCHECKED_CAST")
                    val parsed = analyzeRegistryTagTuple(directiveReader as DirectiveStringReader<AnalyzingResourceCreator>, registry, analyzingResult == null, false)
                    analyzingResult?.combineWith(parsed.analyzingResult)
                    return if(isInlineTag) Predicate { false } else null
                }
                val parsed = parseRegistryTagTuple(directiveReader, registry)
                if(parsed is RawResourceRegistryEntryList<*>) {
                    PackratParserAdditionalArgs.stringifiedArgument.getOrNull()?.run {
                        stringified.add(Either.left("#"))
                        stringified.add(Either.right(parsed.resource))
                    }
                    return Predicate { false }
                }
                return Predicate {
                    parsed.contains(testeeProjection(it))
                }
            } catch(e: Exception) {
                state.errors.add(cursor, null, e)
            }
            return null
        }
    }

    interface CursorAwareException {
        fun `command_crafter$getCursor`(): Int
    }
    class CursorAwareExceptionWrapper(exception: Exception, val cursor: Int) : Exception(exception.message, exception), CursorAwareException {
        override fun `command_crafter$getCursor`(): Int {
            return cursor
        }
    }
}