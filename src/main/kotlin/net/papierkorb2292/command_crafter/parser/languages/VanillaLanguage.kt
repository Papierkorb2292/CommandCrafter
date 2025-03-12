package net.papierkorb2292.command_crafter.parser.languages

import com.mojang.brigadier.ImmutableStringReader
import com.mojang.brigadier.ParseResults
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.context.*
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import com.mojang.datafixers.util.Either
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.Decoder
import net.minecraft.command.CommandSource
import net.minecraft.command.SingleCommandAction
import net.minecraft.command.argument.CommandFunctionArgumentType
import net.minecraft.command.argument.packrat.ParsingRule
import net.minecraft.command.argument.packrat.ParsingState
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryWrapper
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.registry.entry.RegistryEntryList
import net.minecraft.registry.tag.TagEntry
import net.minecraft.screen.ScreenTexts
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.CommandOutput
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.function.FunctionBuilder
import net.minecraft.server.function.Macro
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.StringIdentifiable
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.editor.debugger.helper.StringRangeContainer
import net.papierkorb2292.command_crafter.editor.debugger.helper.withExtension
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.BreakpointCondition
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.BreakpointConditionParser
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugInformation
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionElementDebugInformation
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingClientCommandSource
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.TokenType
import net.papierkorb2292.command_crafter.editor.processing.helper.*
import net.papierkorb2292.command_crafter.editor.processing.partial_id_autocomplete.CompletionItemsPartialIdGenerator
import net.papierkorb2292.command_crafter.helper.getOrNull
import net.papierkorb2292.command_crafter.mixin.parser.TagEntryAccessor
import net.papierkorb2292.command_crafter.parser.*
import net.papierkorb2292.command_crafter.parser.helper.*
import org.eclipse.lsp4j.*
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Predicate
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
                reader.onlyReadEscapedMultiline = !easyNewLine
                val parsed = parseCommand(reader, source)
                reader.onlyReadEscapedMultiline = false
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
                    val position = AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.lines)
                    result.diagnostics += Diagnostic(
                        Range(position, position.advance()),
                        "Unknown or invalid command on line \"${position.line + 1}\" (Do not use a preceding forwards slash.)"
                    )
                    reader.skip()
                }
                if(reader.canRead() && reader.peek() == '$') {
                    //Macros aren't analyzed, but FunctionBuilder does some validation
                    val startCursor = reader.cursor
                    try {
                        FunctionBuilderAccessor_Parser.init<ServerCommandSource>().addMacroCommand(
                            readMacro(reader),
                            reader.currentLine,
                            ServerCommandSource(
                                CommandOutput.DUMMY,
                                Vec3d.ZERO,
                                Vec2f.ZERO,
                                null,
                                4,
                                "",
                                ScreenTexts.EMPTY,
                                null,
                                null
                            )
                        )
                    } catch(e: IllegalArgumentException) {
                        throw CursorAwareExceptionWrapper(e, startCursor)
                    }
                    continue
                }
                //Let command start at cursor 0, so completions don't overlap with suggestRootNode
                reader.cutReadChars()

                reader.onlyReadEscapedMultiline = !easyNewLine
                val parseResults = reader.dispatcher.parse(reader, source)
                advanceToParseResults(parseResults, reader)
                analyzeParsedCommand(parseResults, result, reader)

                val exception = parseResults.exceptions.entries.maxByOrNull { it.value.cursor }
                if(exception != null)
                    throw exception.value
                if(easyNewLine) {
                    if (parseResults.context.range.isEmpty) {
                        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand()
                            .createWithContext(parseResults.reader)
                    }
                } else if (reader.canRead()) {
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
                    reader.skipSpaces()
                    if(!reader.canRead() || reader.scopeStack.element().closure.endsClosure(reader)) {
                        // Analyze last whitespace and then loop should end
                        continue
                    }
                    if (reader.peek() != '\n') {
                        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(reader)
                    } else reader.skip()
                } else {
                    reader.onlyReadEscapedMultiline = false
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
                reader.onlyReadEscapedMultiline = false
                val startPosition =
                    AnalyzingResult.getPositionFromCursor(reader.cursorMapper.mapToSource(reader.readSkippingChars + exceptionCursor), reader.lines)
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
            reader.onlyReadEscapedMultiline = !easyNewLine
            val parsed = parseCommand(reader, source)
            reader.onlyReadEscapedMultiline = false
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
            reader.onlyReadEscapedMultiline = true
            val macro = reader.readLine()
            reader.onlyReadEscapedMultiline = false
            return if(macro.startsWith('$')) macro.substring(1) else macro
        }
        reader.cursorMapper.addMapping(reader.absoluteCursor, reader.skippingCursor, reader.nextLineEnd - reader.cursor)
        if(reader.peek() == '$')
            reader.skip()
        val macroBuilder = StringBuilder(reader.readLine())
        var indentStartCursor = reader.cursor - 1
        while(reader.tryReadIndentation { it > reader.currentIndentation }) {
            val skippedChars = reader.cursor - indentStartCursor
            reader.string = reader.string.substring(0, indentStartCursor - 1) + ' ' + reader.string.substring(reader.cursor) //Also removes newline
            reader.cursor = indentStartCursor
            reader.skippedChars += skippedChars
            reader.readCharacters += skippedChars
            macroBuilder.append(' ')
            reader.cursorMapper.addMapping(reader.absoluteCursor, reader.skippingCursor, reader.nextLineEnd - reader.cursor)
            macroBuilder.append(reader.readLine())
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
            completionsChannel = AnalyzingResult.LANGUAGE_COMPLETION_CHANNEL
        )
    }

    fun analyzeParsedCommand(result: ParseResults<CommandSource>, analyzingResult: AnalyzingResult, reader: DirectiveStringReader<AnalyzingResourceCreator>) {
        var contextBuilder = result.context
        var parentNode = getAnalyzingParsedRootNode(contextBuilder.rootNode, 0)
        while(contextBuilder != null) {
            for (parsedNode in contextBuilder.nodes) {
                analyzeCommandNode(
                    parsedNode,
                    parentNode,
                    contextBuilder,
                    analyzingResult,
                    reader
                )
                parentNode = parsedNode
            }
            contextBuilder = contextBuilder.child
        }
        tryAnalyzeNextNode(
            analyzingResult,
            parentNode,
            result.context,
            reader
        )
    }

    // Add root suggestions at the start of new lines for easyNewLine commands,
    // since at the start of the line there wouldn't be enough indentation to continue the previous command, so a new command can start there
    private fun addRootSuggestionsForImprovedCommandGap(
        gapRange: StringRange,
        analyzingResult: AnalyzingResult,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        commandSource: CommandSource
    ) {
        if(!isReaderEasyNextLine(reader))
            // There can't be any improved command gaps
            return
        val gapReader = reader.copy()
        gapReader.cursor = gapRange.start
        gapReader.readLine()
        while(gapReader.cursor <= gapRange.end) {
            val lineStart = gapReader.cursor
            gapReader.readLine()
            val lineEnd = gapReader.cursor
            val indentEnd = min(lineEnd, lineStart + gapReader.currentIndentation)
            // Suggestions end when the node starts (gapRange) or could start (indentEnd), but if the line is empty gapRange.end would be at the start of the line,
            // which would put gapRange.end - 1 before the start of the line, so max is used to prevent that
            val suggestionEnd = max(lineStart, min(indentEnd, gapRange.end - 1))
            suggestRootNode(gapReader, StringRange(lineStart, suggestionEnd), commandSource, analyzingResult)
        }
    }

    private fun analyzeCommandNode(
        parsedNode: ParsedCommandNode<CommandSource>,
        parentNode: ParsedCommandNode<CommandSource>,
        contextBuilder: CommandContextBuilder<CommandSource>,
        analyzingResult: AnalyzingResult,
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
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
            reader.readCharacters = (parsedNode as CursorOffsetContainer).`command_crafter$getReadCharacters`()
            reader.skippedChars = (parsedNode as CursorOffsetContainer).`command_crafter$getSkippedChars`()
            val completionReader = reader.copy()
            try {
                val nodeAnalyzingResult = analyzingResult.copyInput()
                try {
                    node.`command_crafter$analyze`(
                        context,
                        StringRange(
                            parsedNode.range.start,
                            MathHelper.clamp(parsedNode.range.end, parsedNode.range.start, context.input.length)
                        ),
                        reader,
                        nodeAnalyzingResult,
                        node.name
                    )
                } catch(e: Exception) {
                    CommandCrafter.LOGGER.debug("Error while analyzing command node ${node.name}", e)
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
                        completionReader,
                        contextBuilder,
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
        additionalCompletions: AnalyzingResult? = null,
        rootCompletions: AnalyzingResult? = null,
        completionsChannel: String = AnalyzingResult.LANGUAGE_COMPLETION_CHANNEL
    ) {
        val completionParentNode = parentNode.node.resolveRedirects()
        analyzingResult.addCompletionProvider(
            completionsChannel,
            AnalyzingResult.RangedDataProvider(
                StringRange(
                    parentNode.range.end + 1,
                    parsedNodeRange.end
                )
            ) { cursor ->
                val sourceCursor = analyzingResult.mappingInfo.cursorMapper.mapToSource(cursor)
                val rootCompletionProvider = rootCompletions?.getCompletionProviderForCursor(sourceCursor)
                if(rootCompletionProvider != null)
                    return@RangedDataProvider rootCompletionProvider.dataProvider(sourceCursor)

                val endCursor = cursor - completionReader.readSkippingChars
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
                        it.toCompletionItem(completionReader)
                    } + suggestionFutures.flatMap {
                        (it.get() as CompletionItemsContainer).`command_crafter$getCompletionItems`()
                            ?: emptyList()
                    }
                    if(contextBuilder.source is AnalyzingClientCommandSource) {
                        // Partial Completions are added clientside, so they aren't added twice
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
            },
            true
        )
    }

    private fun tryAnalyzeNextNode(analyzingResult: AnalyzingResult, parentNode: ParsedCommandNode<CommandSource>, context: CommandContextBuilder<CommandSource>, reader: DirectiveStringReader<AnalyzingResourceCreator>) {
        if(isReaderEasyNextLine(reader)) {
            // Don't skip more if a whitespace was already skipped, because the command parser won't skip both
            if(reader.canRead(0) && reader.peek(-1) != ' ') {
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
        } else if(reader.canRead() && reader.peek() == ' ')
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
            ) return
        analyzeCommandNode(
            furthestParsedContext.nodes.last(),
            parentNode,
            furthestParsedContext,
            analyzingResult,
            furthestParsedReader
        )
    }

    object VanillaLanguageType : LanguageManager.LanguageType {
        enum class VanillaLanguageOptions(val optionName: String) : StringIdentifiable {
            ALL_FEATURES("improved"),
            EASY_NEW_LINE("easyNewLine"),
            INLINE_RESOURCES("inlineResources");

            override fun asString() = optionName
        }
        
        override val argumentDecoder: Decoder<Language> = StringIdentifiable.createCodec(VanillaLanguageOptions.entries::toTypedArray).map {
            when(it!!) {
                VanillaLanguageOptions.EASY_NEW_LINE -> VanillaLanguage(easyNewLine = true, inlineResources = false)
                VanillaLanguageOptions.INLINE_RESOURCES -> VanillaLanguage(easyNewLine = false, inlineResources = true)
                VanillaLanguageOptions.ALL_FEATURES -> VanillaLanguage(easyNewLine = true, inlineResources = true)
            }
        }
    }

    companion object {
        const val ID = "vanilla"

        val SUGGESTIONS_FULL_INPUT = ThreadLocal<DirectiveStringReader<AnalyzingResourceCreator>>()

        private val DOUBLE_SLASH_EXCEPTION = SimpleCommandExceptionType(Text.literal("Unknown or invalid command  (if you intended to make a comment, use '#' not '//')"))
        private val COMMAND_NEEDS_NEW_LINE_EXCEPTION = SimpleCommandExceptionType(Text.of("Command doesn't end with a new line"))

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

        private val INVALID_TUPLE = SimpleCommandExceptionType(Text.of("Encountered invalid tuple"))

        fun <T> parseRawRegistryTagTuple(
            reader: DirectiveStringReader<RawZipResourceCreator>,
            registry: RegistryWrapper.Impl<T>,
        ): RawResourceRegistryEntryList<T>
                = RawResourceRegistryEntryList(parseRawTagTupleEntries(reader, RawResource.RawResourceType(RegistryKeys.getTagPath(registry.key), "json")) {
                entryReader -> Either.left(parseRawTagEntry(entryReader))
        })

        fun <T> parseParsedRegistryTagTuple(
            reader: DirectiveStringReader<ParsedResourceCreator>,
            registry: RegistryWrapper.Impl<T>,
        ): GeneratedRegistryEntryList<T> {
            val entries = parseTagTupleEntries(reader, ::parseTagEntry)
            return GeneratedRegistryEntryList(registry).apply {
                reader.resourceCreator.registryTags += ParsedResourceCreator.AutomaticResource(
                    idSetter,
                    ParsedResourceCreator.ParsedTag(registry.key, entries)
                )
            }
        }

        fun <T> analyzeRegistryTagTuple(
            reader: DirectiveStringReader<AnalyzingResourceCreator>,
            registry: RegistryWrapper.Impl<T>,
        ): AnalyzedRegistryEntryList<T> {
            val analyzingResult = AnalyzingResult(reader.fileMappingInfo, AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.lines))
            analyzeTagTupleEntries(reader, analyzingResult) { entryReader, entryAnalyzingResult ->
                val startCursor = reader.cursor
                val pos = AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.lines)
                try {
                    val entry = parseTagEntry(entryReader)
                    if(!entry.resolve(object: TagEntry.ValueGetter<Unit> {
                            override fun direct(id: Identifier, required: Boolean): Unit? {
                                @Suppress("UNCHECKED_CAST")
                                if(registry.getOptional(RegistryKey.of(registry.key as RegistryKey<out Registry<T>>, id)).isPresent) return Unit
                                entryAnalyzingResult.diagnostics += Diagnostic(
                                    Range(pos, pos.advance(reader.cursor - startCursor)),
                                    "Unknown id '$id'"
                                )
                                return null
                            }

                            override fun tag(id: Identifier): Collection<Unit> {
                                return emptyList()
                            }

                        }) { }) return@analyzeTagTupleEntries
                } catch(e: CommandSyntaxException) {
                    analyzingResult.diagnostics += Diagnostic(
                        Range(pos, pos.advance(reader.cursor - startCursor)),
                        e.message
                    )
                    return@analyzeTagTupleEntries
                }
                analyzingResult.semanticTokens.add(pos.line, pos.character, reader.cursor - startCursor, TokenType.PARAMETER, 0)
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
            if(reader.peek() != '(') {
                return null
            }
            return RawResourceFunctionArgument(parseRawTagTupleEntries(reader, RawResource.FUNCTION_TAG_TYPE) entry@{ entryReader ->
                if(entryReader.canRead() && entryReader.peek() == '{') {
                    return@entry Either.right(parseRawInlineFunction(reader, source))
                }
                return@entry Either.left(parseRawTagEntry(reader))
            }, true)
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
            if(reader.peek() != '(') {
                return null
            }
            val entries = parseTagTupleEntries(reader) entry@{ entryReader ->
                if(entryReader.canRead() && entryReader.peek() == '{') {
                    val result = TagEntry.create(ParsedResourceCreator.PLACEHOLDER_ID)
                    parseParsedInlineFunction(reader, source, (result as TagEntryAccessor)::setId)
                    return@entry result
                }
                parseTagEntry(reader)
            }
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
        ): AnalyzedFunctionArgument? {
            if(!reader.canRead()) {
                return null
            }
            val analyzingResult = AnalyzingResult(reader.fileMappingInfo, AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.lines))
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
            } else if(reader.peek() == '{') {
                analyzeInlineFunction(reader, source, analyzingResult)
            } else if(reader.peek() == '(') {
                analyzeTagTupleEntries(reader, analyzingResult) entry@{ entryReader, entryAnalyzingResult ->
                    if (entryReader.peek() == '{') {
                        analyzeInlineFunction(entryReader, source, entryAnalyzingResult)
                        return@entry
                    }
                    analyzeTagEntry(entryReader, entryAnalyzingResult)
                }
            } else {
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
            entryParser: (DirectiveStringReader<*>) -> TagEntry,
        ): List<TagEntry> {
            reader.expect('(')
            reader.skipWhitespace()
            if(reader.canRead() && reader.peek() == ')') {
                reader.skip()
                return Collections.emptyList()
            }
            val entries: MutableList<TagEntry> = ArrayList()
            while(reader.canRead()) {
                entries.add(entryParser(reader))
                reader.skipWhitespace()
                if(!reader.canRead()) break
                if(reader.peek() == ')') {
                    reader.skip()
                    return entries
                }
                reader.expect(',')
                reader.skipWhitespace()
            }
            throw INVALID_TUPLE.createWithContext(reader)
        }

        private fun <ResourceCreator> parseRawTagTupleEntries(
            reader: DirectiveStringReader<ResourceCreator>,
            type: RawResource.RawResourceType,
            entryParser: (DirectiveStringReader<ResourceCreator>) -> Either<String, RawResource>,
        ): RawResource {
            val resource = RawResource(type)
            reader.expect('(')
            reader.skipWhitespace()
            if(reader.canRead() && reader.peek() == ')') {
                reader.skip()
                resource.content += Either.left("{\"values\":[]}")
                return resource
            }
            val stringBuilder = StringBuilder().append("{\"values\":[")
            while(reader.canRead()) {
                stringBuilder.append('"')
                entryParser(reader).ifLeft {
                    stringBuilder.append(it)
                }.ifRight {
                    resource.content += Either.left(stringBuilder.toString())
                    stringBuilder.clear()
                    resource.content += Either.right(it)
                }
                stringBuilder.append('"')
                reader.skipWhitespace()
                if(!reader.canRead()) break
                if(reader.peek() == ')') {
                    reader.skip()
                    resource.content += Either.left(stringBuilder.append("]}").toString())
                    return resource
                }
                reader.expect(',')
                stringBuilder.append(',')
                reader.skipWhitespace()
            }
            throw INVALID_TUPLE.createWithContext(reader)
        }

        private fun <ResourceCreator> analyzeTagTupleEntries(
            reader: DirectiveStringReader<ResourceCreator>,
            analyzingResult: AnalyzingResult,
            entryAnalyzer: (DirectiveStringReader<ResourceCreator>, AnalyzingResult) -> Unit,
        ) {
            reader.expect('(')
            reader.skipWhitespace()
            if(reader.canRead() && reader.peek() == ')') {
                reader.skip()
                return
            }

            while(reader.canRead()) {
                val entryAnalyzingResult = AnalyzingResult(reader.fileMappingInfo, AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.lines))
                entryAnalyzer(reader, entryAnalyzingResult)
                analyzingResult.combineWith(entryAnalyzingResult)
                reader.skipWhitespace()
                if(!reader.canRead()) break
                if(reader.peek() == ')') {
                    reader.skip()
                    return
                }
                if(reader.peek() != ',') {
                    val pos = AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.lines)
                    analyzingResult.diagnostics += Diagnostic(
                        Range(pos, pos.advance()),
                        "Expected ','"
                    )
                    return
                }
                reader.skip()
                reader.skipWhitespace()
            }
            val pos = AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.lines)
            analyzingResult.diagnostics += Diagnostic(
                Range(pos, pos.advance()),
                "Encountered invalid tuple"
            )
        }

        private fun parseTagEntry(reader: DirectiveStringReader<*>): TagEntry {
            val startCursor = reader.skippingCursor
            val referencesTag = reader.peek() == '#'
            if(referencesTag) {
                reader.skip()
            }
            val id = Identifier.fromCommandInput(reader)
            val tagEntry =
                if(referencesTag) TagEntry.createTag(id)
                else TagEntry.create(id)
            (tagEntry as StringRangeContainer).`command_crafter$setRange`(StringRange(
                reader.cursorMapper.mapToSource(startCursor),
                reader.cursorMapper.mapToSource(reader.skippingCursor)
            ))
            return tagEntry
        }

        private fun analyzeTagEntry(reader: DirectiveStringReader<*>, analyzingResult: AnalyzingResult) {
            val startCursor = reader.cursor
            val pos = AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.lines)
            if(reader.peek() == '#') {
                reader.skip()
            }
            try {
                Identifier.fromCommandInput(reader)
            } catch(e: CommandSyntaxException) {
                analyzingResult.diagnostics += Diagnostic(
                    Range(pos, pos.advance(reader.cursor - startCursor)),
                    e.message
                )
                return
            }
            analyzingResult.semanticTokens.add(pos.line, pos.character, reader.cursor - startCursor, TokenType.PARAMETER, 0)
        }

        private fun parseRawTagEntry(reader: DirectiveStringReader<*>): String {
            val startCursor = reader.cursor
            if(reader.peek() == '#') {
                reader.skip()
            }
            Identifier.fromCommandInput(reader)
            return reader.string.substring(startCursor, reader.cursor)
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
        override fun parse(state: ParsingState<StringReader>): Optional<Predicate<Testee>> {
            val reader = state.reader
            val cursor = state.cursor
            if(!isReaderInlineResources(reader) || !reader.canRead() || reader.peek() != '(')
                return Optional.empty()

            try {
                when(val parsed = parseRegistryTagTuple(reader as DirectiveStringReader<*>, registry)) {
                    is AnalyzedRegistryEntryList<*> -> {
                        PackratParserAdditionalArgs.analyzingResult.getOrNull()?.combineWith(parsed.analyzingResult)
                        return Optional.of(Predicate { false })
                    }
                    is GeneratedRegistryEntryList<*> -> {
                        return Optional.of(Predicate {
                            parsed.contains(testeeProjection(it))
                        })
                    }
                    is RawResourceRegistryEntryList<*> -> {
                        PackratParserAdditionalArgs.stringifiedArgument.getOrNull()?.run {
                            add(Either.left("#"))
                            add(Either.right(parsed.resource))
                        }
                        return Optional.of(Predicate { false })
                    }
                }
            } catch(e: Exception) {
                state.errors.add(cursor, null /*TODO InlineTagRule Suggestions*/, e)
            }
            return Optional.empty()
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