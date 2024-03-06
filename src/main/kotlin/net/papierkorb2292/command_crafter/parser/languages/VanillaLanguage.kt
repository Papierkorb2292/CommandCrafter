package net.papierkorb2292.command_crafter.parser.languages

import com.mojang.brigadier.ParseResults
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.context.CommandContextBuilder
import com.mojang.brigadier.context.ContextChain
import com.mojang.brigadier.context.ParsedArgument
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.brigadier.tree.CommandNode
import com.mojang.datafixers.util.Either
import net.minecraft.command.SingleCommandAction
import net.minecraft.command.argument.CommandFunctionArgumentType
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.registry.Registry
import net.minecraft.registry.entry.RegistryEntryList
import net.minecraft.registry.tag.TagEntry
import net.minecraft.registry.tag.TagManagerLoader
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.function.FunctionBuilder
import net.minecraft.server.function.Macro
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.debugger.helper.withExtension
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.BreakpointCondition
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.BreakpointConditionParser
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugInformation
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionElementDebugInformation
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.TokenType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingCommandNode
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.advance
import net.papierkorb2292.command_crafter.mixin.editor.debugger.FunctionBuilderAccessor
import net.papierkorb2292.command_crafter.mixin.parser.TagEntryAccessor
import net.papierkorb2292.command_crafter.parser.*
import net.papierkorb2292.command_crafter.parser.helper.*
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import java.util.*
import kotlin.math.max
import kotlin.math.min

data class VanillaLanguage(val easyNewLine: Boolean = false, val inlineResources: Boolean = false) : Language {

    override fun parseToVanilla(
        reader: DirectiveStringReader<RawZipResourceCreator>,
        source: ServerCommandSource,
        resource: RawResource,
    ) {
        reader.endStatement()
        while (reader.canRead() && reader.currentLanguage == this) {
            if (skipComments(reader)) {
                reader.endStatement()
                continue
            }
            if (reader.peek() == '\n') {
                reader.skip()
                reader.endStatement()
                continue
            }
            reader.saveIndentation()
            throwIfSlashPrefix(reader, reader.currentLine)
            if(reader.canRead() && reader.peek() == '$') {
                //Can't verify syntax on macros
                if(easyNewLine) {
                    resource.content += Either.left('$' + readEasyNewLineMacro(reader) + '\n')
                } else {
                    resource.content += Either.left(reader.readLine() + '\n')
                }
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
            reader.endStatement()
        }
    }

    override fun analyze(
        reader: DirectiveStringReader<AnalyzingResourceCreator>,
        source: ServerCommandSource,
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

        reader.endStatementAndAnalyze(result)
        while (reader.canRead() && reader.currentLanguage == this) {
            val startCursor = reader.absoluteCursor
            if(skipComments(reader)) {
                AnalyzingResult.getInlineRangesBetweenCursors(startCursor, reader.absoluteCursor, reader.lines) { line: Int, cursor: Int, length: Int ->
                    result.semanticTokens.add(line, cursor, length, TokenType.COMMENT, 0)
                }
                reader.endStatementAndAnalyze(result)
                continue
            }
            if(reader.peek() == '\n') {
                reader.skip()
                reader.endStatementAndAnalyze(result)
                continue
            }
            reader.saveIndentation()
            var parseResults: ParseResults<ServerCommandSource>? = null
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
                    //Macros aren't analyzed
                    if(easyNewLine) {
                        readEasyNewLineMacro(reader)
                    } else {
                        reader.readLine()
                    }
                    reader.endStatementAndAnalyze(result)
                    continue
                }

                if(!easyNewLine) {
                    reader.escapedMultilineCursorMapper = ProcessedInputCursorMapper()
                }
                reader.onlyReadEscapedMultiline = !easyNewLine
                parseResults = reader.dispatcher.parse(reader, source)
                createCommandSemantics(parseResults, result, reader)
                reader.escapedMultilineCursorMapper = null

                if(easyNewLine) {
                    val exceptions = parseResults.exceptions
                    if (exceptions.isNotEmpty()) {
                        throw exceptions.values.first()
                    }
                    if (parseResults.context.range.isEmpty) {
                        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand()
                            .createWithContext(parseResults.reader)
                    }
                } else if (parseResults.reader.canRead()) {
                    throw CommandManager.getException(parseResults)!!
                }
                val string = parseResults.reader.string
                val contextChain = ContextChain.tryFlatten(parseResults.context.build(string))
                if(contextChain.isEmpty) {
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parseResults.reader)
                }

                advanceToParseResults(parseResults, reader)
                parseResults = null // Don't accidentally skip to the now completed reader when catching the 'NEEDS_NEW_LINE_EXCEPTION'

                if(easyNewLine) {
                    reader.skipSpaces()
                    if (reader.canRead() && reader.peek() != '\n') {
                        if (!reader.scopeStack.element().closure.endsClosure(reader)) throw COMMAND_NEEDS_NEW_LINE_EXCEPTION.createWithContext(
                            reader
                        )
                    } else reader.skip()
                } else {
                    reader.onlyReadEscapedMultiline = false
                    reader.skip()
                }
            } catch(e: Exception) {
                val exceptionCursor =
                    if (e is CommandSyntaxException && e.cursor != -1) {
                        val cursor = e.cursor + reader.readCharacters
                        if(parseResults != null)
                            advanceToParseResults(parseResults, reader)
                        cursor
                    }
                    else {
                        if(parseResults != null)
                            advanceToParseResults(parseResults, reader)
                        reader.absoluteCursor
                    }
                reader.onlyReadEscapedMultiline = false
                val startPosition =
                    AnalyzingResult.getPositionFromCursor(exceptionCursor, reader.lines)
                result.diagnostics += Diagnostic(
                    Range(
                        startPosition,
                        Position(startPosition.line, reader.lines[startPosition.line].length)
                    ),
                    e.message,
                    DiagnosticSeverity.Error,
                    null
                )

                while (true) {
                    if (!reader.canRead() || reader.read() == '\n')
                        break
                }
            }
            reader.endStatementAndAnalyze(result)
        }
    }

    override fun parseToCommands(
        reader: DirectiveStringReader<ParsedResourceCreator?>,
        source: ServerCommandSource,
        builder: FunctionBuilder<ServerCommandSource>,
    ): FunctionDebugInformation? {
        reader.endStatement()
        val elementBreakpointParsers = mutableListOf<FunctionElementDebugInformation.FunctionElementProcessor>()
        while (reader.canRead() && reader.currentLanguage == this) {
            if (skipComments(reader)) {
                reader.endStatement()
                continue
            }
            if(reader.peek() == '\n') {
                reader.skip()
                reader.endStatement()
                continue
            }
            reader.saveIndentation()
            throwIfSlashPrefix(reader, reader.currentLine)
            if(reader.canRead() && reader.peek() == '$') {
                reader.skip()
                val startCursor = reader.absoluteCursor
                val cursorMapper = if(easyNewLine) ProcessedInputCursorMapper() else null
                builder.addMacroCommand(
                    if(easyNewLine) readEasyNewLineMacro(reader, cursorMapper) else reader.readLine(),
                    reader.currentLine
                )
                val macroLines = (builder as FunctionBuilderAccessor).macroLines
                @Suppress("UNCHECKED_CAST")
                elementBreakpointParsers += FunctionElementDebugInformation.MacroElementProcessor(
                    macroLines.size - 1,
                    StringRange.between(startCursor, reader.absoluteCursor),
                    macroLines.last() as Macro.VariableLine<ServerCommandSource>,
                    cursorMapper
                )
                reader.endStatement()
                continue
            }
            val startCursor = reader.readCharacters
            reader.onlyReadEscapedMultiline = !easyNewLine
            val parsed = parseCommand(reader, source)
            reader.onlyReadEscapedMultiline = false
            val string = parsed.reader.string
            val contextChain = ContextChain.tryFlatten(parsed.context.build(string)).orElseThrow {
                CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parsed.reader)
            }
            elementBreakpointParsers += FunctionElementDebugInformation.CommandContextElementProcessor(
                contextChain.topContext,
                startCursor
            )
            builder.addAction(SingleCommandAction.Sourced(string, contextChain))
            reader.endStatement()
        }
        return reader.resourceCreator?.run {
            FunctionElementDebugInformation(
                elementBreakpointParsers,
                reader.lines,
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

    private fun parseCommand(reader: DirectiveStringReader<*>, source: ServerCommandSource): ParseResults<ServerCommandSource> {
        try {
            val parseResults: ParseResults<ServerCommandSource> = reader.dispatcher.parse(reader, source)
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
                    if (!reader.scopeStack.element().closure.endsClosure(reader)) throw COMMAND_NEEDS_NEW_LINE_EXCEPTION.createWithContext(
                        reader
                    )
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

    private fun readEasyNewLineMacro(reader: DirectiveStringReader<*>, cursorMapper: ProcessedInputCursorMapper? = null): String {
        if(!reader.canRead()) return ""
        if(reader.peek() == '$')
            reader.skip()
        val firstLineStart = reader.absoluteCursor
        val macroBuilder = StringBuilder(reader.readLine())
        cursorMapper?.addMapping(firstLineStart, 0, reader.absoluteCursor - firstLineStart)
        while(reader.tryReadIndentation { it > reader.currentIndentation }) {
            macroBuilder.append(' ')
            val start = reader.absoluteCursor
            val prevMacroLength = macroBuilder.length
            macroBuilder.append(reader.readLine())
            cursorMapper?.addMapping(start, prevMacroLength, reader.absoluteCursor - start)
        }
        return macroBuilder.toString()
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
                if (node is UnparsableCommandNode) {
                    for(part in node.`command_crafter$unparseNode`(
                        context,
                        parsedNode.range,
                        DirectiveStringReader(
                            listOf(UnparsableCommandNode.unparseNodeFromStringRange(context, parsedNode.range)),
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
                    stringBuilder.append(UnparsableCommandNode.unparseNodeFromStringRange(context, parsedNode.range))
                }
            }
            contextBuilder = contextBuilder.child
            context = context.child
        }
        resource.content += Either.left(stringBuilder.append('\n').toString())
    }

    fun createCommandSemantics(result: ParseResults<ServerCommandSource>, analyzingResult: AnalyzingResult, reader: DirectiveStringReader<AnalyzingResourceCreator>) {
        var contextBuilder = result.context
        var context = contextBuilder.build(result.reader.string)

        val semanticTokens = analyzingResult.semanticTokens

        val readCharactersOffset = if(reader.escapedMultilineCursorMapper == null) reader.readCharacters else 0
        semanticTokens.cursorMapper = reader.escapedMultilineCursorMapper

        while(contextBuilder != null && context != null) {
            for (parsedNode in contextBuilder.nodes) {
                val node = parsedNode.node
                if (node is AnalyzingCommandNode) {
                    try {
                        semanticTokens.cursorOffset = readCharactersOffset + parsedNode.range.start
                        node.`command_crafter$analyze`(
                            context,
                            StringRange(parsedNode.range.start, max(min(parsedNode.range.end, context.input.length), parsedNode.range.start)),
                            reader,
                            analyzingResult,
                            node.name
                        )
                    } catch(_: CommandSyntaxException) { }
                    semanticTokens.cursorOffset = 0
                }
            }
            if(context.child == null && contextBuilder.nodes.isNotEmpty()) {
                tryAnalyzeNextNode(result, analyzingResult, contextBuilder.nodes.last().node.children, contextBuilder, reader)
            }
            contextBuilder = contextBuilder.child
            context = context.child
        }

        semanticTokens.cursorOffset = 0
        semanticTokens.cursorMapper = null
    }

    private fun tryAnalyzeNextNode(result: ParseResults<ServerCommandSource>, analyzingResult: AnalyzingResult, nodes: Collection<CommandNode<ServerCommandSource>>, context: CommandContextBuilder<ServerCommandSource>, reader: DirectiveStringReader<AnalyzingResourceCreator>) {
        if (nodes.size != 1) return
        val nextNode = nodes.first()
        if (nextNode !is ArgumentCommandNode<*, *>) return
        val prevReader = result.reader
        if(!prevReader.canRead()) return
        val newReader: StringReader
        if(prevReader is DirectiveStringReader<*>) {
            newReader = prevReader.copy()
        } else {
            newReader = StringReader(prevReader.string)
            newReader.cursor = prevReader.cursor
        }
        val start = newReader.cursor
        try {
            val argumentResult = nextNode.type.parse(newReader)
            context.withArgument(nextNode.name, ParsedArgument(start, newReader.cursor, argumentResult))
        } catch(ignored: Exception) { }

        try {
            analyzingResult.semanticTokens.cursorOffset = if(reader.escapedMultilineCursorMapper == null) reader.readCharacters + start else start
            (nextNode as AnalyzingCommandNode).`command_crafter$analyze`(
                context.build(result.reader.string),
                StringRange(start, max(min(newReader.cursor, result.reader.string.length), start)),
                reader,
                analyzingResult,
                nextNode.name
            )
        } catch(_: CommandSyntaxException) { }
        analyzingResult.semanticTokens.cursorOffset = 0
    }

    object VanillaLanguageType : LanguageManager.LanguageType {
        private const val ALL_FEATURES_OPTION = "improved"
        private const val EASY_NEW_LINE_OPTION = "easyNewLine"
        private const val INLINE_RESOURCES_OPTION = "inlineResources"

        override fun createFromArguments(args: Map<String, String?>, currentLine: Int): Language {
            if(args.containsKey(ALL_FEATURES_OPTION)) {
                args.keys.filter { it != ALL_FEATURES_OPTION }.forEach {
                    throw IllegalArgumentException("Error parsing language arguments: Unknown parameter $it for '${ID}' on line $currentLine")
                }
                val allFeaturesParsed = when(val allFeaturesArg = args[ALL_FEATURES_OPTION]) {
                    "false" -> false
                    null, "true" -> true
                    else -> throw IllegalArgumentException("Error parsing language arguments: Unknown argument '$allFeaturesArg' for parameter '$ALL_FEATURES_OPTION' of '$ID' on line $currentLine. Must be either 'true', 'false' or removed (defaults to 'true')")
                }
                return VanillaLanguage(easyNewLine = allFeaturesParsed, inlineResources = allFeaturesParsed)
            }
            val easyNewLineArg = if(args.containsKey(EASY_NEW_LINE_OPTION)) args[EASY_NEW_LINE_OPTION] ?: "true" else null
            val inlineResourcesArg = if(args.containsKey(INLINE_RESOURCES_OPTION)) args[INLINE_RESOURCES_OPTION] ?: "true" else null

            args.keys.filter { it != EASY_NEW_LINE_OPTION && it != INLINE_RESOURCES_OPTION }.forEach {
                throw IllegalArgumentException("Error parsing language arguments: Unknown parameter $it for '${ID}' on line $currentLine")
            }

            return VanillaLanguage(
                easyNewLine = when(easyNewLineArg) {
                    null, "false" -> false
                    "true" -> true
                    else -> throw IllegalArgumentException("Error parsing language arguments: Unknown argument '$easyNewLineArg' for parameter '$EASY_NEW_LINE_OPTION' of '$ID' on line $currentLine. Must be either 'false', 'true' or removed (defaults to 'false')")
                },
                inlineResources = when(inlineResourcesArg) {
                    null, "false" -> false
                    "true" -> true
                    else -> throw IllegalArgumentException("Error parsing language arguments: Unknown argument '$inlineResourcesArg' for parameter '$INLINE_RESOURCES_OPTION' of '$ID' on line $currentLine. Must be either 'false', 'true' or removed (defaults to 'false')")
                }
            )
        }

        override fun createFromArgumentsAndAnalyze(
            args: Map<String, LanguageManager.AnalyzingLanguageArgument>,
            currentLine: Int,
            analyzingResult: AnalyzingResult,
            lines: List<String>,
        ): Language? {
            val allFeaturesArg = args[ALL_FEATURES_OPTION]
            if(allFeaturesArg != null) {
                args.entries.filter { it.key != ALL_FEATURES_OPTION }.forEach {
                    analyzingResult.diagnostics += it.value.createDiagnostic("Error parsing language arguments: Unknown parameter ${it.key} for '${ID}' on line $currentLine", lines)
                }
                val allFeaturesParsed = when(allFeaturesArg.value) {
                    "false" -> false
                    null, "true" -> true
                    else -> {
                        analyzingResult.diagnostics += allFeaturesArg.createDiagnostic("Error parsing language arguments: Unknown argument '${allFeaturesArg.value}' for parameter '$ALL_FEATURES_OPTION' of '$ID' on line $currentLine. Must be either 'true', 'false' or removed (defaults to 'true')", lines)
                        true
                    }
                }
                return VanillaLanguage(easyNewLine = allFeaturesParsed, inlineResources = allFeaturesParsed)
            }
            val easyNewLineArg = args["easyNewLine"]
            val inlineResourcesArg = args["inlineResources"]

            args.entries.filter { it.key != EASY_NEW_LINE_OPTION && it.key != INLINE_RESOURCES_OPTION }.forEach {
                analyzingResult.diagnostics += it.value.createDiagnostic("Error parsing language arguments: Unknown parameter ${it.key} for '${ID}' on line $currentLine", lines)
            }

            return VanillaLanguage(
                easyNewLine = when(easyNewLineArg?.run { value ?: "true" }) {
                    null, "false" -> false
                    "true" -> true
                    else -> {
                        analyzingResult.diagnostics += easyNewLineArg.createDiagnostic("Error parsing language arguments: Unknown argument '${easyNewLineArg.value}' for parameter 'easyNewLine' of '${ID}' on line $currentLine. Must be either 'false', 'true' or removed (defaults to 'false')", lines)
                        return null
                    }
                },
                inlineResources = when(inlineResourcesArg?.run { value ?: "true" }) {
                    null, "false" -> false
                    "true" -> true
                    else -> {
                        analyzingResult.diagnostics += inlineResourcesArg.createDiagnostic("Error parsing language arguments: Unknown argument '${inlineResourcesArg.value}' for parameter 'inlineResources' of '${ID}' on line $currentLine. Must be either 'false', 'true' or removed (defaults to 'false')", lines)
                        return null
                    }
                }
            )
        }
    }

    companion object {
        const val ID = "vanilla"

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

        fun isReaderEasyNextLine(reader: StringReader): Boolean {
            return reader is DirectiveStringReader<*> && reader.currentLanguage is VanillaLanguage && (reader.currentLanguage as VanillaLanguage).easyNewLine
        }

        fun isReaderInlineResources(reader: StringReader): Boolean {
            return reader is DirectiveStringReader<*> && reader.currentLanguage is VanillaLanguage && (reader.currentLanguage as VanillaLanguage).inlineResources
        }

        private val INVALID_TUPLE = SimpleCommandExceptionType(Text.of("Encountered invalid tuple"))

        fun <T> parseRawRegistryTagTuple(
            reader: DirectiveStringReader<RawZipResourceCreator>,
            registry: Registry<T>,
        ): RawResourceRegistryEntryList<T>
                = RawResourceRegistryEntryList(parseRawTagTupleEntries(reader, RawResource.RawResourceType(TagManagerLoader.getPath(registry.key), "json")) {
                entryReader -> Either.left(parseRawTagEntry(entryReader))
        })

        fun <T> parseParsedRegistryTagTuple(
            reader: DirectiveStringReader<ParsedResourceCreator>,
            registry: Registry<T>,
        ): GeneratedRegistryEntryList<T> {
            val entries = parseTagTupleEntries(reader, ::parseTagEntry)
            return GeneratedRegistryEntryList(registry).apply {
                reader.resourceCreator.registryTags += ParsedResourceCreator.AutomaticResource(
                    idSetter,
                    ParsedResourceCreator.ParsedTag(DynamicRegistryManager.Entry(registry.key, registry), entries)
                )
            }
        }

        fun <T> analyzeRegistryTagTuple(
            reader: DirectiveStringReader<AnalyzingResourceCreator>,
            registry: Registry<T>,
        ): AnalyzedRegistryEntryList<T> {
            val analyzingResult = AnalyzingResult(reader.lines)
            analyzeTagTupleEntries(reader, analyzingResult) { entryReader, entryAnalyzingResult ->
                val startCursor = reader.cursor
                val pos = AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.lines)
                try {
                    val entry = parseTagEntry(entryReader)
                    if(!entry.resolve(object: TagEntry.ValueGetter<Unit> {
                            override fun direct(id: Identifier): Unit? {
                                if(registry.containsId(id)) return Unit
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
            registry: Registry<T>,
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
            })
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
            source: ServerCommandSource,
            analyzingResult: AnalyzingResult,
        ) {
            reader.expect('{')
            LanguageManager.analyse(reader, source, analyzingResult, NestedVanillaClosure(reader.currentLanguage!!))
        }

        fun analyzeImprovedFunctionReference(
            reader: DirectiveStringReader<AnalyzingResourceCreator>,
            source: ServerCommandSource,
        ): AnalyzedFunctionArgument? {
            if(!reader.canRead()) {
                return null
            }
            val analyzingResult = AnalyzingResult(reader.lines)
            if(reader.canRead(4) && reader.string.startsWith("this", reader.cursor)) {
                val position = AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.lines)
                analyzingResult.semanticTokens.add(position.line, position.character, 4, TokenType.KEYWORD, 0)
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

        fun parseImprovedFunctionReference(reader: DirectiveStringReader<*>, source: ServerCommandSource): CommandFunctionArgumentType.FunctionArgument? {
            reader.withNoMultilineRestriction<Nothing> {
                it.resourceCreator.run {
                    if(this is RawZipResourceCreator) {
                        @Suppress("UNCHECKED_CAST") //It's not, it was checked in the previous 'if' statement
                        return parseRawImprovedFunctionReference(it as DirectiveStringReader<RawZipResourceCreator>, source)
                    }
                    if(this is ParsedResourceCreator) {
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
                entryAnalyzer(reader, analyzingResult)
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
            val referencesTag = reader.peek() == '#'
            if(referencesTag) {
                reader.skip()
            }
            val id = Identifier.fromCommandInput(reader)
            return if(referencesTag) {
                TagEntry.createTag(id)
            } else {
                TagEntry.create(id)
            }
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
            val line = reader.currentLine
            reader.skipSpaces()
            if(!reader.canRead() || reader.peek() != '\n') {
                return true
            }
            var newLineIndentation = 0
            while(reader.canRead() && reader.peek() == '\n') {
                reader.skip()
                newLineIndentation = reader.readIndentation()
            }
            if(newLineIndentation > reader.currentIndentation) {
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
        override fun endsClosure(reader: DirectiveStringReader<*>) = reader.trySkipWhitespace {
            reader.canRead() && reader.peek() == '}'
        }

        override fun skipClosureEnd(reader: DirectiveStringReader<*>) {
            reader.skip()
        }
    }
}