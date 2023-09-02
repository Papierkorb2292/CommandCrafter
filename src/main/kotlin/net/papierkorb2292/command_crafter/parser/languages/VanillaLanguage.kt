package net.papierkorb2292.command_crafter.parser.languages

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.ParseResults
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.CommandContextBuilder
import com.mojang.brigadier.context.ParsedArgument
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.brigadier.tree.CommandNode
import com.mojang.datafixers.util.Either
import net.minecraft.command.argument.CommandFunctionArgumentType
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.registry.Registry
import net.minecraft.registry.entry.RegistryEntryList
import net.minecraft.registry.tag.TagEntry
import net.minecraft.registry.tag.TagManagerLoader
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.function.CommandFunction
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.debugger.helper.DebugInformationContainer
import net.papierkorb2292.command_crafter.editor.debugger.helper.withExtension
import net.papierkorb2292.command_crafter.editor.debugger.server.FunctionDebugInformation
import net.papierkorb2292.command_crafter.editor.debugger.server.FunctionElementDebugInformation
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.BreakpointCondition
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.BreakpointConditionParser
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionBreakpointLocation
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionPauseContext
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.TokenType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingCommandNode
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.advance
import net.papierkorb2292.command_crafter.mixin.parser.TagEntryAccessor
import net.papierkorb2292.command_crafter.parser.*
import net.papierkorb2292.command_crafter.parser.helper.*
import org.eclipse.lsp4j.*
import java.util.*
import kotlin.math.max
import kotlin.math.min

enum class VanillaLanguage : Language {
    NORMAL {
        override fun parseToVanilla(
            reader: DirectiveStringReader<RawZipResourceCreator>,
            source: ServerCommandSource,
            resource: RawResource,
        ) {
            reader.endStatement()
            while(reader.canRead() && reader.currentLanguage == this) {
                val line = StringReader(reader.readLine().trimStart())
                if(!line.canRead() || line.peek() == '#') {
                    reader.endStatement()
                    continue
                }
                writeCommand(parseCommand(line, reader.currentLine - 1, reader.dispatcher, source), resource, reader)
                reader.endStatement()
            }
        }

        override fun parseToCommands(
            reader: DirectiveStringReader<ParsedResourceCreator?>,
            source: ServerCommandSource,
        ): Pair<List<CommandFunction.Element>, FunctionDebugInformation?> {
            val result: MutableList<Pair<ParseResults<ServerCommandSource>, Int>> = ArrayList()
            reader.endStatement()
            while(reader.canRead() && reader.currentLanguage == this) {
                val startCursor = reader.absoluteCursor
                val line = StringReader(reader.readLine().trimStart())
                if(!line.canRead() || line.peek() == '#') {
                    reader.endStatement()
                    continue
                }
                result.add(parseCommand(line, reader.currentLine - 1, reader.dispatcher, source) to startCursor)
                reader.endStatement()
            }
            return result.map {
                CommandFunction.CommandElement(it.first)
            } to reader.resourceCreator?.run {
                FunctionElementDebugInformation(
                    result,
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

        override fun analyze(
            reader: DirectiveStringReader<AnalyzingResourceCreator>,
            source: ServerCommandSource,
            result: AnalyzingResult,
        ) {
            reader.endStatementAndAnalyze(result)
            while(reader.canRead() && reader.currentLanguage == this) {
                val line = StringReader(reader.readLine())
                line.skipWhitespace()
                if (!line.canRead()) {
                    reader.endStatementAndAnalyze(result)
                    continue
                }
                if (line.peek() == '#') {
                    val position = AnalyzingResult.getPositionFromCursor(reader.absoluteCursor - line.remainingLength - 1, reader.lines)
                    result.semanticTokens.add(position.line, position.character, line.remainingLength, TokenType.COMMENT, 0)
                    reader.endStatementAndAnalyze(result)
                    continue
                }

                try {
                    if (line.canRead() && line.peek() == '/') {
                        if(line.canRead(2) && line.peek(1) == '/') {
                            throw DOUBLE_SLASH_EXCEPTION.createWithContext(line)
                        }
                        val position = AnalyzingResult.getPositionFromCursor(reader.absoluteCursor - line.remainingLength - 1, reader.lines)
                        result.diagnostics += Diagnostic(
                            Range(position, position.advance()),
                            "Unknown or invalid command on line \"${position.line + 1}\" (Do not use a preceding forwards slash.)"
                        )
                        line.skip()
                    }
                    val parseResults = reader.dispatcher.parse(line, source)
                    createCommandSemantics(
                        parseResults,
                        result,
                        reader
                    )
                    line.cursor = parseResults.reader.cursor
                    if (parseResults.reader.canRead()) {
                        throw CommandManager.getException(parseResults)!!
                    }
                    if(isIncomplete(parseResults))
                        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(line)
                } catch (e: Exception) {
                    val exceptionCursor =
                        if(e is CommandSyntaxException && e.cursor != -1) e.cursor
                        else line.cursor
                    val startPosition = AnalyzingResult.getPositionFromCursor(exceptionCursor + reader.readCharacters, reader.lines)
                    result.diagnostics += Diagnostic(
                        Range(
                            startPosition,
                            Position(startPosition.line, reader.lines[startPosition.line].length)
                        ),
                        e.message,
                        DiagnosticSeverity.Error,
                        null
                    )
                }
                reader.endStatementAndAnalyze(result)
            }
        }

        private fun parseCommand(reader: StringReader, line: Int, dispatcher: CommandDispatcher<ServerCommandSource>, source: ServerCommandSource): ParseResults<ServerCommandSource> {
            throwIfSlashPrefix(reader, line)
            try {
                val parseResults: ParseResults<ServerCommandSource> = dispatcher.parse(reader, source)
                if (parseResults.reader.canRead()) {
                    throw CommandManager.getException(parseResults)!!
                }
                return parseResults
            } catch (commandSyntaxException: CommandSyntaxException) {
                throw IllegalArgumentException("Whilst parsing command on line $line: ${commandSyntaxException.message}")
            }
        }
    },
    IMPROVED {
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
                if(reader.peek() == '\n') {
                    reader.skip()
                    reader.endStatement()
                    reader.currentLine++
                    continue
                }
                reader.readIndentation()
                throwIfSlashPrefix(reader, reader.currentLine)
                writeCommand(parseCommand(reader, source), resource, reader)
                reader.endStatement()
            }
        }

        override fun parseToCommands(
            reader: DirectiveStringReader<ParsedResourceCreator?>,
            source: ServerCommandSource,
        ): Pair<List<CommandFunction.Element>, FunctionDebugInformation?> {
            reader.endStatement()
            val result: MutableList<Pair<ParseResults<ServerCommandSource>, Int>> = ArrayList()
            while (reader.canRead() && reader.currentLanguage == this) {
                if (skipComments(reader)) {
                    reader.endStatement()
                    continue
                }
                if(reader.peek() == '\n') {
                    reader.skip()
                    reader.endStatement()
                    reader.currentLine++
                    continue
                }
                val startCursor = reader.absoluteCursor
                reader.readIndentation()
                throwIfSlashPrefix(reader, reader.currentLine)
                result.add(parseCommand(reader, source) to startCursor)
                reader.endStatement()
            }
            return result.map {
                CommandFunction.CommandElement(it.first)
            } to reader.resourceCreator?.run {
                FunctionElementDebugInformation(
                    result,
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
                    reader.endStatementAndAnalyze(result)
                    AnalyzingResult.getInlineRangesBetweenCursors(startCursor, reader.absoluteCursor, reader.lines) { line: Int, cursor: Int, length: Int ->
                        result.semanticTokens.add(line, cursor, length, TokenType.COMMENT, 0)
                    }
                    continue
                }
                if(reader.peek() == '\n') {
                    reader.skip()
                    reader.endStatementAndAnalyze(result)
                    reader.currentLine++
                    continue
                }
                reader.readIndentation()
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

                    parseResults = reader.dispatcher.parse(reader, source)
                    createCommandSemantics(
                        parseResults,
                        result,
                        reader
                    )

                    val exceptions = parseResults.exceptions
                    if (exceptions.isNotEmpty()) {
                        throw exceptions.values.first()
                    }
                    if (parseResults.context.range.isEmpty) {
                        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parseResults.reader)
                    }
                    if(isIncomplete(parseResults))
                        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parseResults.reader)

                    advanceToParseResults(parseResults, reader)
                    parseResults = null // Don't accidentally skip to the now completed reader when catching the 'NEEDS_NEW_LINE_EXCEPTION'

                    reader.skipSpaces()
                    if (reader.canRead() && reader.peek() != '\n') {
                        if (!reader.scopeStack.element().closure.endsClosure(reader)) throw COMMAND_NEEDS_NEW_LINE_EXCEPTION.createWithContext(reader)
                    }
                    else reader.skip()
                    reader.currentLine++
                } catch (e: Exception) {
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

        private val COMMAND_NEEDS_NEW_LINE_EXCEPTION = SimpleCommandExceptionType(Text.of("Command doesn't end with a new line"))

        private fun parseCommand(reader: DirectiveStringReader<*>, source: ServerCommandSource): ParseResults<ServerCommandSource> {
            try {
                val parseResults: ParseResults<ServerCommandSource> = reader.dispatcher.parse(reader, source)
                val exceptions = parseResults.exceptions
                if (exceptions.isNotEmpty()) {
                    throw exceptions.values.first()
                }
                parseResults.reader.run {
                    if(this is DirectiveStringReader<*>) {
                        reader.copyFrom(this)
                        toCompleted()
                    }
                }
                if (parseResults.context.range.isEmpty) {
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parseResults.reader)
                }
                reader.skipSpaces()
                if (reader.canRead() && reader.peek() != '\n') {
                    if (!reader.scopeStack.element().closure.endsClosure(reader)) throw COMMAND_NEEDS_NEW_LINE_EXCEPTION.createWithContext(reader)
                }
                else reader.skip()
                reader.currentLine++
                return parseResults
            } catch (commandSyntaxException: CommandSyntaxException) {
                throw IllegalArgumentException("Whilst parsing command on line ${reader.currentLine}: ${commandSyntaxException.message}")
            }
        }
    };

    companion object {
        const val ID = "vanilla"

        private val DOUBLE_SLASH_EXCEPTION = SimpleCommandExceptionType(Text.literal("Unknown or invalid command  (if you intended to make a comment, use '#' not '//')"))

        fun skipComments(reader: DirectiveStringReader<*>): Boolean {
            var foundAny = false
            while(true) {
                if(!reader.trySkipWhitespace {
                    if(reader.canRead() && reader.peek() == '#') {
                        @Suppress("ControlFlowWithEmptyBody")
                        while (reader.canRead() && reader.read() != '\n') { }
                        reader.currentLine++
                        return@trySkipWhitespace true
                    }
                    false
                }) {
                    return foundAny
                }
                foundAny = true
            }
        }

        fun isReaderImproved(reader: StringReader): Boolean {
            return reader is DirectiveStringReader<*> && reader.currentLanguage == IMPROVED
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
            registry: Registry<T>
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
            reader.resourceCreator.run {
                if(this is RawZipResourceCreator) {
                    @Suppress("UNCHECKED_CAST") //It's not, it was checked in the previous 'if' statement
                    return parseRawRegistryTagTuple(reader as DirectiveStringReader<RawZipResourceCreator>, registry)
                }
                if(this is ParsedResourceCreator) {
                    @Suppress("UNCHECKED_CAST") //It's not, it was checked in the previous 'if' statement
                    return parseParsedRegistryTagTuple(reader as DirectiveStringReader<ParsedResourceCreator>, registry)
                }
                if(this is AnalyzingResourceCreator) {
                    @Suppress("UNCHECKED_CAST") //It's not, it was checked in the previous 'if' statement
                    return analyzeRegistryTagTuple(reader as DirectiveStringReader<AnalyzingResourceCreator>, registry)
                }
                throw ParsedResourceCreator.RESOURCE_CREATOR_UNAVAILABLE_EXCEPTION.createWithContext(reader)
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
            val parsed = LanguageManager.parseToCommandsWithDebugInformation(reader, source, ImprovedVanillaClosure)
            val function = CommandFunction(
                ParsedResourceCreator.PLACEHOLDER_ID,
                parsed.first
            )
            parsed.second?.run {
                @Suppress("UNCHECKED_CAST", "KotlinConstantConditions")
                (function as DebugInformationContainer<FunctionBreakpointLocation, FunctionPauseContext>)
                    .`command_crafter$setDebugInformation`(this)
            }
            reader.resourceCreator.functions += ParsedResourceCreator.AutomaticResource(functionIdSetter, function)
            functionIdSetter.range = StringRange(startCursor, reader.absoluteCursor)
            reader.resourceCreator.originResourceIdSetEventStack.element()(idSetter)
            reader.resourceCreator.popOriginResource()
        }

        private fun parseRawInlineFunction(reader: DirectiveStringReader<RawZipResourceCreator>, source: ServerCommandSource): RawResource {
            reader.expect('{')
            val resource = RawResource(RawResource.FUNCTION_TYPE)
            LanguageManager.parseToVanilla(reader, source, resource, ImprovedVanillaClosure)
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
            analyzingResult: AnalyzingResult
        ) {
            reader.expect('{')
            LanguageManager.analyse(reader, source, analyzingResult, ImprovedVanillaClosure)
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
                val pos = AnalyzingResult.getPositionFromCursor(reader.absoluteCursor, reader.lines)
                analyzingResult.diagnostics += Diagnostic(
                    Range(pos, pos.advance()),
                    "Invalid function reference"
                )
            }
            return AnalyzedFunctionArgument(analyzingResult)
        }

        fun parseImprovedFunctionReference(reader: DirectiveStringReader<*>, source: ServerCommandSource): CommandFunctionArgumentType.FunctionArgument? {
            reader.resourceCreator.run {
                if(this is RawZipResourceCreator) {
                    @Suppress("UNCHECKED_CAST") //It's not, it was checked in the previous 'if' statement
                    return parseRawImprovedFunctionReference(reader as DirectiveStringReader<RawZipResourceCreator>, source)
                }
                if(this is ParsedResourceCreator) {
                    @Suppress("UNCHECKED_CAST") //It's not, it was checked in the previous 'if' statement
                    return parseParsedImprovedFunctionReference(reader as DirectiveStringReader<ParsedResourceCreator>, source)
                }
                if(this is AnalyzingResourceCreator) {
                    @Suppress("UNCHECKED_CAST") //It's not, it was checked in the previous 'if' statement
                    return analyzeImprovedFunctionReference(reader as DirectiveStringReader<AnalyzingResourceCreator>, source)
                }
                throw ParsedResourceCreator.RESOURCE_CREATOR_UNAVAILABLE_EXCEPTION.createWithContext(reader)
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
            entryAnalyzer: (DirectiveStringReader<ResourceCreator>, AnalyzingResult) -> Unit
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
                reader.currentLine++
                reader.skip()
                val newLineStart = reader.cursor
                reader.skipSpaces()
                newLineIndentation = reader.cursor - newLineStart
            }
            if(newLineIndentation > reader.currentIndentation) {
                return true
            }
            reader.cursor = cursor
            reader.currentLine = line
            return false
        }
    }

    object ImprovedVanillaClosure : Language.LanguageClosure {
        override val startLanguage: Language = IMPROVED
        override fun endsClosure(reader: DirectiveStringReader<*>) = reader.trySkipWhitespace {
            reader.canRead() && reader.peek() == '}'
        }

        override fun skipClosureEnd(reader: DirectiveStringReader<*>) {
            reader.skip()
        }
    }

    object VanillaLanguageType : LanguageManager.LanguageType {
        override fun createFromArguments(args: Map<String, String?>, currentLine: Int): Language {
            for(key in args.keys) {
                if(key != "improved") {
                    throw IllegalArgumentException("Error parsing language arguments: Unknown parameter $key for '$ID' on line $currentLine")
                }
            }
            if(!args.containsKey("improved")) {
                return NORMAL
            }
            return when (val improved = args["improved"]) {
                "false" -> NORMAL
                null, "true" -> IMPROVED
                else -> throw IllegalArgumentException("Error parsing language arguments: Unknown argument '$improved' for parameter 'improved' of '$ID' on line $currentLine. Must be either 'false', 'true' or removed (defaults to 'false')")
            }
        }

        override fun createFromArgumentsAndAnalyze(
            args: Map<String, LanguageManager.AnalyzingLanguageArgument>,
            currentLine: Int,
            analyzingResult: AnalyzingResult,
            lines: List<String>
        ): Language? {
            for((parameter, value) in args) {
                if(parameter != "improved") {
                    analyzingResult.diagnostics += value.createDiagnostic("Error parsing language arguments: Unknown parameter $parameter for '$ID' on line $currentLine", lines)
                }
            }
            val improvedArgument = args["improved"] ?: return NORMAL
            return when (val improved = improvedArgument.value) {
                "false" -> NORMAL
                null, "true" -> IMPROVED
                else -> {
                    analyzingResult.diagnostics += improvedArgument.createDiagnostic("Error parsing language arguments: Unknown argument '$improved' for parameter 'improved' of '$ID' on line $currentLine. Must be either 'false', 'true' or removed (defaults to 'false')", lines)
                    null
                }
            }
        }
    }

    fun throwIfSlashPrefix(reader: StringReader, line: Int) {
        if (reader.peek() == '/') {
            reader.skip()
            require(reader.peek() != '/') { "Unknown or invalid command on line $line (if you intended to make a comment, use '#' not '//')" }
            val string2: String = reader.readUnquotedString()
            throw IllegalArgumentException("Unknown or invalid command on line $line (did you mean '$string2'? Do not use a preceding forwards slash.)")
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

        while(contextBuilder != null && context != null) {
            for (parsedNode in contextBuilder.nodes) {
                val node = parsedNode.node
                if (node is AnalyzingCommandNode) {
                    try {
                        node.`command_crafter$analyze`(
                            context,
                            StringRange(parsedNode.range.start, max(min(parsedNode.range.end, context.input.length), parsedNode.range.start)),
                            reader,
                            analyzingResult,
                            node.name
                        )
                    } catch(_: CommandSyntaxException) { }
                }
            }
            if(context.child == null) {
                tryAnalyzeNextNode(result, analyzingResult, contextBuilder.nodes.last().node.children, contextBuilder, reader)
            }
            contextBuilder = contextBuilder.child
            context = context.child
        }
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
            (nextNode as AnalyzingCommandNode).`command_crafter$analyze`(
                context.build(result.reader.string),
                StringRange(start, max(min(newReader.cursor, result.reader.string.length), start)),
                reader,
                analyzingResult,
                nextNode.name
            )
        } catch(_: CommandSyntaxException) { }
    }

    fun isIncomplete(parseResults: ParseResults<*>): Boolean {
        var context: CommandContextBuilder<*> = parseResults.context ?: return true
        while(true) {
            if(context.command != null)
                return false
            context = context.child ?: return true
            if(context.nodes.isNotEmpty())
                return false
        }
    }

    object VanillaBreakpointConditionParser : BreakpointConditionParser {
        override fun parseCondition(condition: String?, hitCondition: String?): BreakpointCondition {
            //TODO
            return object : BreakpointCondition {
                override fun checkCondition(context: CommandContext<ServerCommandSource>): Boolean {
                    return true
                }

                override fun checkHitCondition(context: CommandContext<ServerCommandSource>): Boolean {
                    return true
                }

            }
        }
    }
}