package net.papierkorb2292.command_crafter.test

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.command.MacroInvocation
import net.papierkorb2292.command_crafter.helper.IntList.Companion.intListOf
import net.papierkorb2292.command_crafter.parser.helper.MacroCursorMapperProvider
import net.minecraft.command.CommandSource
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.test.TestContext
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.SemanticTokensBuilder
import net.papierkorb2292.command_crafter.editor.processing.TokenType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.clampCompletionToCursor
import net.papierkorb2292.command_crafter.helper.IntList
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import net.papierkorb2292.command_crafter.parser.Language
import net.papierkorb2292.command_crafter.parser.LanguageManager
import net.papierkorb2292.command_crafter.parser.ParsedResourceCreator
import net.papierkorb2292.command_crafter.parser.RawZipResourceCreator
import net.papierkorb2292.command_crafter.parser.helper.RawResource
import net.papierkorb2292.command_crafter.parser.helper.SplitProcessedInputCursorMapper
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage
import net.papierkorb2292.command_crafter.test.TestSnapshotHelper.assertEqualsSnapshot
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.spongepowered.asm.mixin.MixinEnvironment
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

object TestCommandCrafter {
    val projectDirectory = Path.of("").toAbsolutePath().parent.parent // Current directory is CommandCrafter/build/gametest/
    val snapshotDirectory = projectDirectory.resolve("tests/__snapshots__")
    val inputDirectory = projectDirectory.resolve("tests/inputs")

    @GameTest
    fun exampleTest(context: TestContext) {
        context.assertEquals(1, 1, Text.literal("example"))
        context.complete()
    }

    @GameTest
    fun exampleSnapshotTest(context: TestContext) {
        context.assertEqualsSnapshot("foo", Text.literal("example_snapshot"))
        context.complete()
    }

    @GameTest
    fun auditMixins(context: TestContext) {
        MixinEnvironment.getCurrentEnvironment().audit()
        context.complete()
    }

    @GameTest
    fun testMacroInvocationCursorMapperEndingWithSegment(context: TestContext) {
        val macroInvocation = MacroInvocation.parse("say $(greeting), $(name)!")
        val arguments = listOf("What's up", "your highness")
        @Suppress("CAST_NEVER_SUCCEEDS")
        val cursorMapper = (macroInvocation as MacroCursorMapperProvider).`command_crafter$getCursorMapper`(arguments)
        context.assertEquals(intListOf("".length, "say $(greeting)".length, "say $(greeting), $(name)".length), cursorMapper.sourceCursors,Text.literal("source_cursors"))
        context.assertEquals(intListOf("".length, "say What's up".length, "say What's up, your highness".length), cursorMapper.targetCursors, Text.literal("target_cursors"))
        context.assertEquals(intListOf("say ".length, ", ".length, "!".length), cursorMapper.lengths, Text.literal("lengths"))
        context.complete()
    }

    @GameTest
    fun testMacroInvocationCursorMapperEndingWithVariable(context: TestContext) {
        val macroInvocation = MacroInvocation.parse("say $(message)")
        val arguments = listOf("foo")
        @Suppress("CAST_NEVER_SUCCEEDS")
        val cursorMapper = (macroInvocation as MacroCursorMapperProvider).`command_crafter$getCursorMapper`(arguments)
        context.assertEquals(intListOf("".length, "say $(message)".length), cursorMapper.sourceCursors,Text.literal("source_cursors"))
        context.assertEquals(intListOf("".length, "say foo".length), cursorMapper.targetCursors, Text.literal("target_cursors"))
        context.assertEquals(intListOf("say ".length, "".length), cursorMapper.lengths, Text.literal("lengths"))
        context.complete()
    }

    @GameTest
    fun testSplitProcessedInputCursorMapperContainsCursor(context: TestContext) {
        val cursorMapper = SplitProcessedInputCursorMapper()
        cursorMapper.addMapping(10, 12, 5)
        cursorMapper.addMapping(20, 25, 2)
        context.assertTrue(cursorMapper.containsSourceCursor(11), Text.literal("source cursor in first mapping"))
        context.assertTrue(cursorMapper.containsTargetCursor(16), Text.literal("target cursor in first mapping"))
        context.assertFalse(cursorMapper.containsSourceCursor(17), Text.literal("source cursor between mappings"))
        context.assertFalse(cursorMapper.containsTargetCursor(20), Text.literal("target cursor between mappings"))
        context.assertTrue(cursorMapper.containsSourceCursor(21), Text.literal("source cursor in second mapping"))
        context.assertTrue(cursorMapper.containsTargetCursor(25), Text.literal("target cursor in second mapping"))
        context.assertFalse(cursorMapper.containsSourceCursor(22), Text.literal("source cursor at exclusive end of second mapping"))
        context.assertTrue(cursorMapper.containsSourceCursor(22, true), Text.literal("source cursor at inclusive end of second mapping"))
        context.complete()
    }

    @GameTest
    fun testGetAndRemoveLocationsOfInterest(context: TestContext) {
        val lines = listOf(
            "§first line",
            "second§ §line",
            "third line§",
        )
        val (processedLines, markedLocations) = getAndRemoveMarkedLocations(lines)

        context.assertEquals(listOf(
            "first line",
            "second line",
            "third line",
        ), processedLines, Text.literal("Removing marker characters"))

        context.assertEquals(listOf(
            FileLocation(Position(0, 0), 0),
            FileLocation(Position(1, 6), 17),
            FileLocation(Position(1, 7), 18),
            FileLocation(Position(2, 10), 33)
        ), markedLocations, Text.literal("Parsing marked locations"))

        context.complete()
    }

    @GameTest
    fun testClampCompletionToCursor(context: TestContext) {
        val markedLines = listOf(
            "first line§",
            "s§econd lin§e",
            "§third line",
        )
        val (processedLines, markedLocations) = getAndRemoveMarkedLocations(markedLines)
        val mappingInfo = FileMappingInfo(processedLines)
        // A mapping that covers all the second line except the first and last character
        mappingInfo.cursorMapper.addMapping(
            markedLocations[1].absoluteCursor,
            markedLocations[1].absoluteCursor,
            markedLocations[2].absoluteCursor - markedLocations[1].absoluteCursor
        )

        context.assertEquals(
            markedLocations[0].position,
            markedLocations[1].position.clampCompletionToCursor(0, 0, mappingInfo),
            Text.literal("Clamp to previous line without cursor mapper")
        )
        context.assertEquals(
            markedLocations[3].position,
            markedLocations[1].position.clampCompletionToCursor(2, mappingInfo.accumulatedLineLengths[1], mappingInfo),
            Text.literal("Clamp to later line without cursor mapper")
        )
        context.assertEquals(
            markedLocations[2].position,
            markedLocations[3].position.clampCompletionToCursor(1, mappingInfo.cursorMapper.sourceCursors[0], mappingInfo),
            Text.literal("Clamp to previous line with cursor mapper")
        )
        context.assertEquals(
            markedLocations[1].position,
            markedLocations[0].position.clampCompletionToCursor(1, mappingInfo.cursorMapper.sourceCursors[0], mappingInfo),
            Text.literal("Clamp to later line with cursor mapper")
        )

        context.complete()
    }

    @GameTest
    fun testSemanticTokensOverlay(context: TestContext) {
        val baseTokens = SemanticTokensBuilder(FileMappingInfo(listOf()))
        val overlayTokens = SemanticTokensBuilder(FileMappingInfo(listOf()))
        val expectedTokens = SemanticTokensBuilder(FileMappingInfo(listOf()))

        baseTokens.add(0, 5, 10, TokenType.NUMBER, 0)
        overlayTokens.add(1, 5, 10, TokenType.STRING, 0)
        baseTokens.add(2, 0, 20, TokenType.NUMBER, 0)
        overlayTokens.add(2, 5, 10, TokenType.STRING, 0)
        baseTokens.add(3, 5, 10, TokenType.NUMBER, 0)
        overlayTokens.add(3, 0, 20, TokenType.STRING, 0)
        baseTokens.add(4, 0, 20, TokenType.NUMBER, 0)
        overlayTokens.add(4, 0, 5, TokenType.STRING, 0)
        overlayTokens.add(4, 15, 5, TokenType.STRING, 0)

        expectedTokens.add(0, 5, 10, TokenType.NUMBER, 0)
        expectedTokens.add(1, 5, 10, TokenType.STRING, 0)
        expectedTokens.add(2, 0, 5, TokenType.NUMBER, 0)
        expectedTokens.add(2, 5, 10, TokenType.STRING, 0)
        expectedTokens.add(2, 15, 5, TokenType.NUMBER, 0)
        expectedTokens.add(3, 0, 20, TokenType.STRING, 0)
        expectedTokens.add(4, 0, 5, TokenType.STRING, 0)
        expectedTokens.add(4, 5, 10, TokenType.NUMBER, 0)
        expectedTokens.add(4, 15, 5, TokenType.STRING, 0)

        baseTokens.overlay(listOf(overlayTokens).iterator())

        context.assertEquals(expectedTokens.build().data, baseTokens.build().data, Text.literal("token data"))
        context.complete()
    }

    @GameTest
    fun testSplitProcessedInputCursorMapperCombineWith(context: TestContext) {
        val sourceMapper = SplitProcessedInputCursorMapper()
        val targetMapper = SplitProcessedInputCursorMapper()

        sourceMapper.addMapping(0, 0, 10)
        sourceMapper.addMapping(12, 10, 10)
        targetMapper.addMapping(0, 0, 8)
        targetMapper.addMapping(15, 8, 5)

        context.assertEqualsSnapshot(sourceMapper.combineWith(targetMapper), Text.literal("result"))

        context.complete()
    }

    @GameTest
    fun testAnalyzingResultAddOffset(context: TestContext) {
        fun createTestAnalyzingResult(fileInfo: FileMappingInfo): AnalyzingResult {
            // All example data is chosen such that it can be compared when this method is called for the entire file once and for the end of the file once (and offset afterward)
            val result = AnalyzingResult(fileInfo, Position())
            val fileRange = StringRange(0, fileInfo.accumulatedLineLengths.last())

            // An example completion that just inserts "Example" at the cursor position everywhere in the file
            result.addCompletionProvider(
                AnalyzingResult.LANGUAGE_COMPLETION_CHANNEL,
                AnalyzingResult.RangedDataProvider(fileRange) { cursor ->
                    CompletableFuture.completedFuture(listOf(CompletionItem().apply {
                        label = "Example"
                        textEdit = Either.forLeft(TextEdit().apply {
                            newText = "Example"
                            val pos = AnalyzingResult.getPositionFromCursor(cursor, fileInfo)
                            range = Range(pos, pos)
                        })
                    }))
                },
                false
            )
            val lastPos = AnalyzingResult.getPositionFromCursor(fileRange.end, fileInfo)
            // An example error at the end of the file
            result.diagnostics += Diagnostic(Range(lastPos, lastPos), "Example")
            // An example semantic token at the end of the file
            result.semanticTokens.add(fileInfo.lines.lastIndex, fileInfo.lines.last().length, 0, TokenType.NUMBER, 0)

            return result
        }

        fun testForFile(firstFilePart: String, secondFilePart: String, description: String) {
            val firstPartLines = firstFilePart.lines()
            val fullResult = createTestAnalyzingResult(FileMappingInfo((firstFilePart + secondFilePart).lines()))
            val partialResult = createTestAnalyzingResult(FileMappingInfo(secondFilePart.lines()))
            val offsetResult = partialResult.addOffset(fullResult, Position(firstPartLines.lastIndex, firstPartLines.last().length), firstFilePart.length)

            val completionExamplePosition = firstFilePart.length + secondFilePart.length/2

            context.assertEquals(
                fullResult.getCompletionProviderForCursor(completionExamplePosition)!!.dataProvider(completionExamplePosition).get(),
                offsetResult.getCompletionProviderForCursor(completionExamplePosition)!!.dataProvider(completionExamplePosition).get(),
                Text.literal("completions from $description")
            )
            context.assertEquals(
                fullResult.diagnostics,
                offsetResult.diagnostics,
                Text.literal("diagnostics from $description")
            )
            context.assertEquals(
                fullResult.semanticTokens.build().data,
                offsetResult.semanticTokens.build().data,
                Text.literal("semantic tokens from $description")
            )
            context.assertEquals(
                fullResult.semanticTokens.lastLine,
                offsetResult.semanticTokens.lastLine,
                Text.literal("semantic tokens last line from $description")
            )
            context.assertEquals(
                fullResult.semanticTokens.lastCursor,
                offsetResult.semanticTokens.lastCursor,
                Text.literal("semantic tokens last cursor from $description")
            )
        }

        val multilineFirstPart = """
            This is
                an example
            text
        """.trimIndent()
        val multilineSecondPart = """
            Here's another
                example text
            but for only half the price
        """.trimIndent()
        val singlelineFirstPart = "Look at that: "
        val singlelineSecondPart = "<-- There's text there"

        testForFile(multilineFirstPart, multilineSecondPart, "multiline + multiline")
        testForFile(multilineFirstPart, singlelineSecondPart, "multiline + singleline")
        testForFile(singlelineFirstPart, multilineSecondPart, "singleline + multiline")
        testForFile(singlelineFirstPart, singlelineSecondPart, "singleline + singleline")

        context.complete()
    }

    @GameTest
    fun testGetInlineRangesBetweenCursors(context: TestContext) {
        val markedLines = """
            Another§ cool example§
            §that I had to write to§ test§
            §this function I§ wrote
        """.trimIndent().lines()
        val (processedLines, markedLocations) = getAndRemoveMarkedLocations(markedLines)

        val mappingInfo = FileMappingInfo(processedLines)

        fun assertInlineRangesEquals(expected: List<Range>, start: FileLocation, end: FileLocation, description: String) {
            val result = mutableListOf<Range>()
            AnalyzingResult.getInlineRangesBetweenCursors(start.absoluteCursor, end.absoluteCursor, mappingInfo) { line, cursor, length ->
                result += Range(Position(line, cursor), Position(line, cursor + length))
            }

            context.assertEquals(expected, result, Text.literal(description))
        }

        assertInlineRangesEquals(listOf(
            Range(markedLocations[0].position, markedLocations[1].position),
            Range(markedLocations[2].position, markedLocations[4].position),
            Range(markedLocations[5].position, markedLocations[6].position),
        ), markedLocations[0], markedLocations[6], "multiline")

        context.complete()
    }

    @GameTest
    fun testCommandSuggestions(context: TestContext) {
        val markedLines = """
            §execute §\
                §if predicate {§\
                    §\
                §}
            
            @language vanilla improved
            §execute
            §    §if predicate {§
                    §
                §}
        """.trimIndent().lines()
        val (processedLines, markedLocations) = getAndRemoveMarkedLocations(markedLines)

        val rootIndices = listOf(0, 6, 7)
        val subcommandIndices = listOf(1, 2, 8)
        val predicateIndices = listOf(3, 4, 5, 9, 10, 11)

        val analyzingResult = analyseCommand(context, processedLines)

        for(rootIndex in rootIndices) {
            val absoluteCursor = markedLocations[rootIndex].absoluteCursor
            context.assertTrue(
                analyzingResult
                    .getCompletionProviderForCursor(absoluteCursor)!!
                    .dataProvider(absoluteCursor)
                    .get()
                    .any { it.label == "execute" },
                Text.literal("Root suggestions for marker at index $rootIndex")
            )
        }
        for(rootIndex in subcommandIndices) {
            val absoluteCursor = markedLocations[rootIndex].absoluteCursor
            context.assertTrue(
                analyzingResult
                    .getCompletionProviderForCursor(absoluteCursor)!!
                    .dataProvider(absoluteCursor)
                    .get()
                    .any { it.label == "if" },
                Text.literal("Subcommand suggestions for marker at index $rootIndex")
            )
        }
        for(rootIndex in predicateIndices) {
            val absoluteCursor = markedLocations[rootIndex].absoluteCursor
            context.assertTrue(
                analyzingResult
                    .getCompletionProviderForCursor(absoluteCursor)!!
                    .dataProvider(absoluteCursor)
                    .get()
                    .any { it.label == "condition" },
                Text.literal("Predicate suggestions for marker at index $rootIndex")
            )
        }

        context.complete()
    }

    @GameTest
    fun testMalformedPackratSuggestions(context: TestContext) {
        val markedLines = """
            clear @a §nothing§[d§oesnt_exist,minecraft:custom_name=§"Blue Stone"§] 
        """.trimIndent().lines()
        val (processedLines, markedLocations) = getAndRemoveMarkedLocations(markedLines)

        val analyzingResult = analyseCommand(context, processedLines)

        for((i, location) in markedLocations.withIndex()) {
            context.assertFalse(
                analyzingResult.getCompletionProviderForCursor(location.absoluteCursor)
                    ?.dataProvider(location.absoluteCursor)
                    ?.get()
                    .isNullOrEmpty(),
                Text.literal("Item predicate suggestions for marker at $i")
            )
        }

        context.complete()
    }

    @GameTest
    fun testMultilineCommandsHighlighting(context: TestContext) {
        // With trailing spaces!
        val lines = """
            execute \ 
                run \ 
                say HI!  
            execute run \    
             say HI!
        """.trimIndent().lines()

        val analyzingResult = analyseCommand(context, lines)
        val tokenData = analyzingResult.semanticTokens.build().data

        fun testToken(tokenIndex: Int, expectedLineOffset: Int, expectedCursorOffset: Int, expectedLength: Int) {
            context.assertEquals(expectedLineOffset,   tokenData[5 * tokenIndex + 0], Text.literal("Index $tokenIndex: token line"))
            context.assertEquals(expectedCursorOffset, tokenData[5 * tokenIndex + 1], Text.literal("Index $tokenIndex: token cursor"))
            context.assertEquals(expectedLength,       tokenData[5 * tokenIndex + 2], Text.literal("Index $tokenIndex: token length"))
        }

        testToken(0, 0, 0, 7)
        testToken(1, 1, 4, 3)
        testToken(2, 1, 4, 3)
        testToken(3, 0, 4, 3)
        testToken(4, 1, 0, 7)
        testToken(5, 0, 8, 3)
        testToken(6, 1, 1, 3)
        testToken(7, 0, 4, 3)
        context.complete()
    }

    @GameTest
    fun parseTestFunction(context: TestContext) {
        val lines = """
            # This is a doc \    
                comment
            execute as @a run say HI!
            execute if entity @s \ 
                run tp @s ~ ~1 ~
            @language vanilla improved
            execute
                align xyz run
                summon minecraft:armor_stand
            function {
                say HI!            
            }
        """.trimIndent().lines()
        try {
            testAllFunctionParsers(lines, context)
        } catch(e: CommandSyntaxException) {
            context.throwGameTestException(Text.literal("Error testing function parsers: ${e.message}"))
        }
        context.complete()
    }

    fun testAllFunctionParsers(lines: List<String>, context: TestContext) {
        val id = Identifier.of("test")
        @Suppress("UNCHECKED_CAST")
        val commandDispatcher = context.world.server.commandManager.dispatcher as CommandDispatcher<CommandSource>
        val source = getParsingCommandSource(context)

        val parsedResourceCreator = ParsedResourceCreator(
            id,
            "testPack"
        )
        val originResource = parsedResourceCreator.addOriginResource()
        val parsed = LanguageManager.parseToCommands(
            DirectiveStringReader(
                FileMappingInfo(lines),
                commandDispatcher,
                parsedResourceCreator
            ),
            source,
            Language.TopLevelClosure(VanillaLanguage())
        )
        val characterCount = lines.sumOf { it.length + 1 } - 1 // With newlines after every line except for the last
        originResource(ParsedResourceCreator.ResourceStackInfo(id, StringRange(0, characterCount)))
        parsedResourceCreator.popOriginResource()
        context.assertEqualsSnapshot(parsed.toCommandFunction(id), "parseToCommands_actions")

        val zipResourceCreator = RawZipResourceCreator()
        val parseToVanillaResource = RawResource(RawResource.FUNCTION_TYPE)
        LanguageManager.parseToVanilla(
            DirectiveStringReader(
                FileMappingInfo(lines),
                commandDispatcher,
                zipResourceCreator
            ),
            source,
            parseToVanillaResource,
            Language.TopLevelClosure(VanillaLanguage())
        )
        context.assertEqualsSnapshot(parseToVanillaResource.content, "parse_to_vanilla")
        val analyzingResult = AnalyzingResult(FileMappingInfo(lines), Position())
        LanguageManager.analyse(
            DirectiveStringReader(
                analyzingResult.mappingInfo,
                commandDispatcher,
                AnalyzingResourceCreator(
                    null,
                    "testPack/data/minecraft/function/test.mcfunction"
                )
            ),
            source,
            analyzingResult,
            Language.TopLevelClosure(VanillaLanguage())
        )
        context.assertEqualsSnapshot(analyzingResult, "analyzing_result")
    }

    @GameTest
    fun testMacroHighlighting(context: TestContext) {
        val lines = Files.readAllLines(inputDirectory.resolve("macros.mcfunction"))

        val result = analyseCommand(context, lines)
        context.assertEqualsSnapshot(result.semanticTokens.build().data, "semantic_tokens")
        context.complete()
    }

    private fun analyseCommand(context: TestContext, lines: List<String>): AnalyzingResult {
        @Suppress("UNCHECKED_CAST")
        val commandDispatcher = context.world.server.commandManager.dispatcher as CommandDispatcher<CommandSource>
        val source = getParsingCommandSource(context)
        val analyzingResult = AnalyzingResult(FileMappingInfo(lines), Position())

        LanguageManager.analyse(
            DirectiveStringReader(
                analyzingResult.mappingInfo,
                commandDispatcher,
                AnalyzingResourceCreator(
                    null,
                    "testPack/data/minecraft/function/test.mcfunction"
                )
            ),
            source,
            analyzingResult,
            Language.TopLevelClosure(VanillaLanguage())
        )

        return analyzingResult
    }

    private fun getParsingCommandSource(context: TestContext): ServerCommandSource =
        context.world.server.commandSource
            .withPosition(Vec3d.ZERO) // Default position is the worldspawn, which changes between test so it must be set to another value

    /**
     * To make it easier to reference locations in lines, this method can find any location marked with '§'.
     * Those locations will be returned in order and the '§' characters will be removed from the returned lines.
     */
    private fun getAndRemoveMarkedLocations(lines: List<String>): Pair<List<String>, List<FileLocation>> {
        val processedLines = mutableListOf<String>()
        val foundLocations = mutableListOf<FileLocation>()

        var readCharacters = 0
        for((lineNumber, line) in lines.withIndex()) {
            var locationCount = 0
            line.mapIndexedNotNullTo(foundLocations) { column, c ->
                if(c == '§') {
                    val location = FileLocation(Position(lineNumber, column - locationCount), readCharacters + column - locationCount)
                    locationCount++
                    location
                } else null
            }
            processedLines += line.replace("§", "")
            readCharacters += line.length + 1 - locationCount
        }

        return processedLines to foundLocations
    }

    private data class FileLocation(val position: Position, val absoluteCursor: Int)
}