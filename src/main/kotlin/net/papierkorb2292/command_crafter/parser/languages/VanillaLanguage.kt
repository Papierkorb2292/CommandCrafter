package net.papierkorb2292.command_crafter.parser.languages

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.ParseResults
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.context.CommandContextBuilder
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
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
import net.papierkorb2292.command_crafter.editor.processing.SemanticResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.SemanticTokensBuilder
import net.papierkorb2292.command_crafter.editor.processing.TokenType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.SemanticCommandNode
import net.papierkorb2292.command_crafter.mixin.parser.TagEntryAccessor
import net.papierkorb2292.command_crafter.parser.*
import net.papierkorb2292.command_crafter.parser.helper.*
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import java.util.*

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
        ): List<CommandFunction.Element> {
            val result: MutableList<CommandFunction.Element> = ArrayList()
            reader.endStatement()
            while(reader.canRead() && reader.currentLanguage == this) {
                val line = StringReader(reader.readLine().trimStart())
                if(!line.canRead() || line.peek() == '#') {
                    reader.endStatement()
                    continue
                }
                result.add(CommandFunction.CommandElement(parseCommand(line, reader.currentLine - 1, reader.dispatcher, source)))
                reader.endStatement()
            }
            return result
        }

        override fun analyze(
            reader: DirectiveStringReader<SemanticResourceCreator>,
            source: ServerCommandSource,
            result: AnalyzingResult,
        ) {
            reader.endStatement()
            while(reader.canRead() && reader.currentLanguage == this) {
                val line = StringReader(reader.readLine().trimStart())
                if (!line.canRead()) {
                    reader.endStatement()
                    continue
                }
                if (line.peek() == '#') {
                    result.semanticTokens.addRelative(1, 0, line.remainingLength, TokenType.COMMENT, 0)
                    reader.endStatement()
                    continue
                }

                try {
                    if (line.canRead() && line.peek() == '/')
                        line.skip()
                    val parseResults = reader.dispatcher.parse(line, source)
                    createCommandSemantics(
                        parseResults,
                        result.semanticTokens,
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
                reader.endStatement()
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
        ): List<CommandFunction.Element> {
            reader.endStatement()
            val result: MutableList<CommandFunction.Element> = ArrayList()
            while (reader.canRead() && reader.currentLanguage == this) {
                if (skipComments(reader)) {
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
                result.add(CommandFunction.CommandElement(parseCommand(reader, source)))
                reader.endStatement()
            }
            return result
        }

        override fun analyze(
            reader: DirectiveStringReader<SemanticResourceCreator>,
            source: ServerCommandSource,
            result: AnalyzingResult,
        ) {
            TODO("Not yet implemented")
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

        fun parseArguments(args: Map<String, String?>, line: Int): VanillaLanguage {
            for(key in args.keys) {
                if(key != "improved") {
                    throw IllegalArgumentException("Error parsing language arguments: Unknown parameter $key for '$ID' on line $line")
                }
            }
            if(!args.containsKey("improved")) {
                return NORMAL
            }
            return when (val improved = args["improved"]) {
                "false" -> NORMAL
                null, "true" -> IMPROVED
                else -> throw IllegalArgumentException("Error parsing language arguments: Unknown argument '$improved' for parameter 'improved' of '$ID' on line $line. Must be either 'false', 'true' or removed (defaults to 'false')")
            }
        }

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
                    reader.endStatement()
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
                throw ParsedResourceCreator.RESOURCE_CREATOR_UNAVAILABLE_EXCEPTION.createWithContext(reader)
            }
        }

        private fun parseParsedInlineFunction(
            reader: DirectiveStringReader<ParsedResourceCreator>,
            source: ServerCommandSource,
            idSetter: (Identifier) -> Unit,
        ) {
            reader.expect('{')
            reader.resourceCreator.functions += ParsedResourceCreator.AutomaticResource(
                reader.resourceCreator.addOriginResource(),
                CommandFunction(
                    ParsedResourceCreator.PLACEHOLDER_ID,
                    LanguageManager.parseToCommands(reader, source, ImprovedVanillaClosure)
                )
            )
            reader.resourceCreator.originResourceIdSetEventStack.pop()(idSetter)
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
        override fun endsClosure(reader: StringReader) = reader.canRead() && reader.peek() == '}'

        override fun skipClosureEnd(reader: StringReader) {
            reader.skip()
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

    fun createCommandSemantics(result: ParseResults<ServerCommandSource>, tokens: SemanticTokensBuilder, reader: DirectiveStringReader<SemanticResourceCreator>) {
        var contextBuilder = result.context
        var context = contextBuilder.build(result.reader.string)

        while(contextBuilder != null && context != null) {
            for (parsedNode in contextBuilder.nodes) {
                val node = parsedNode.node
                if (node is SemanticCommandNode) {
                    node.`command_crafter$createSemanticTokens`(
                        context,
                        parsedNode.range,
                        reader,
                        tokens
                    )
                }
            }
            contextBuilder = contextBuilder.child
            context = context.child
        }
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
}