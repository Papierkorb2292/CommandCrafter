package net.papierkorb2292.command_crafter.test

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.RootCommandNode
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.commands.functions.StringTemplate
import net.papierkorb2292.command_crafter.helper.IntList.Companion.intListOf
import net.papierkorb2292.command_crafter.parser.helper.MacroCursorMapperProvider
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.resources.RegistryDataLoader
import net.minecraft.commands.CommandSourceStack
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.Vec3
import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.SemanticTokensBuilder
import net.papierkorb2292.command_crafter.editor.processing.TokenType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.clampCompletionToCursor
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import net.papierkorb2292.command_crafter.parser.Language
import net.papierkorb2292.command_crafter.parser.LanguageManager
import net.papierkorb2292.command_crafter.parser.ParsedResourceCreator
import net.papierkorb2292.command_crafter.parser.RawZipResourceCreator
import net.papierkorb2292.command_crafter.parser.helper.RawResource
import net.papierkorb2292.command_crafter.parser.helper.SplitProcessedInputCursorMapper
import net.papierkorb2292.command_crafter.parser.languages.MacroAnalyzingCrawlerRunner
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
    fun exampleTest(context: GameTestHelper) {
        context.assertValueEqual(1, 1, Component.literal("example"))
        context.succeed()
    }

    @GameTest
    fun exampleSnapshotTest(context: GameTestHelper) {
        context.assertEqualsSnapshot("foo", Component.literal("example_snapshot"))
        context.succeed()
    }

    @GameTest
    fun auditMixins(context: GameTestHelper) {
        MixinEnvironment.getCurrentEnvironment().audit()
        context.succeed()
    }

    @GameTest
    fun testMacroInvocationCursorMapperEndingWithSegment(context: GameTestHelper) {
        val macroInvocation = StringTemplate.fromString("say $(greeting), $(name)!")
        val arguments = listOf("What's up", "your highness")
        @Suppress("CAST_NEVER_SUCCEEDS")
        val cursorMapper = (macroInvocation as MacroCursorMapperProvider).`command_crafter$getCursorMapper`(arguments)
        context.assertValueEqual(intListOf("".length, "say $(greeting)".length, "say $(greeting), $(name)".length), cursorMapper.sourceCursors,
            Component.literal("source_cursors"))
        context.assertValueEqual(intListOf("".length, "say What's up".length, "say What's up, your highness".length), cursorMapper.targetCursors, Component.literal("target_cursors"))
        context.assertValueEqual(intListOf("say ".length, ", ".length, "!".length), cursorMapper.lengths, Component.literal("lengths"))
        context.succeed()
    }

    @GameTest
    fun testMacroInvocationCursorMapperEndingWithVariable(context: GameTestHelper) {
        val macroInvocation = StringTemplate.fromString("say $(message)")
        val arguments = listOf("foo")
        @Suppress("CAST_NEVER_SUCCEEDS")
        val cursorMapper = (macroInvocation as MacroCursorMapperProvider).`command_crafter$getCursorMapper`(arguments)
        context.assertValueEqual(intListOf("".length, "say $(message)".length), cursorMapper.sourceCursors, Component.literal("source_cursors"))
        context.assertValueEqual(intListOf("".length, "say foo".length), cursorMapper.targetCursors, Component.literal("target_cursors"))
        context.assertValueEqual(intListOf("say ".length, "".length), cursorMapper.lengths, Component.literal("lengths"))
        context.succeed()
    }

    @GameTest
    fun testSplitProcessedInputCursorMapperContainsCursor(context: GameTestHelper) {
        val cursorMapper = SplitProcessedInputCursorMapper()
        cursorMapper.addMapping(10, 12, 5)
        cursorMapper.addMapping(20, 25, 2)
        context.assertTrue(cursorMapper.containsSourceCursor(11), Component.literal("source cursor in first mapping"))
        context.assertTrue(cursorMapper.containsTargetCursor(16), Component.literal("target cursor in first mapping"))
        context.assertFalse(cursorMapper.containsSourceCursor(17), Component.literal("source cursor between mappings"))
        context.assertFalse(cursorMapper.containsTargetCursor(20), Component.literal("target cursor between mappings"))
        context.assertTrue(cursorMapper.containsSourceCursor(21), Component.literal("source cursor in second mapping"))
        context.assertTrue(cursorMapper.containsTargetCursor(25), Component.literal("target cursor in second mapping"))
        context.assertFalse(cursorMapper.containsSourceCursor(22), Component.literal("source cursor at exclusive end of second mapping"))
        context.assertTrue(cursorMapper.containsSourceCursor(22, true), Component.literal("source cursor at inclusive end of second mapping"))
        context.succeed()
    }

    @GameTest
    fun testGetAndRemoveLocationsOfInterest(context: GameTestHelper) {
        val lines = listOf(
            "§first line",
            "second§ §line",
            "third line§",
        )
        val (processedLines, markedLocations) = getAndRemoveMarkedLocations(lines)

        context.assertValueEqual(listOf(
            "first line",
            "second line",
            "third line",
        ), processedLines, Component.literal("Removing marker characters"))

        context.assertValueEqual(listOf(
            FileLocation(Position(0, 0), 0),
            FileLocation(Position(1, 6), 17),
            FileLocation(Position(1, 7), 18),
            FileLocation(Position(2, 10), 33)
        ), markedLocations, Component.literal("Parsing marked locations"))

        context.succeed()
    }

    @GameTest
    fun testClampCompletionToCursor(context: GameTestHelper) {
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

        context.assertValueEqual(
            markedLocations[0].position,
            markedLocations[1].position.clampCompletionToCursor(0, 0, mappingInfo),
            Component.literal("Clamp to previous line without cursor mapper")
        )
        context.assertValueEqual(
            markedLocations[3].position,
            markedLocations[1].position.clampCompletionToCursor(2, mappingInfo.accumulatedLineLengths[1], mappingInfo),
            Component.literal("Clamp to later line without cursor mapper")
        )
        context.assertValueEqual(
            markedLocations[2].position,
            markedLocations[3].position.clampCompletionToCursor(1, mappingInfo.cursorMapper.sourceCursors[0], mappingInfo),
            Component.literal("Clamp to previous line with cursor mapper")
        )
        context.assertValueEqual(
            markedLocations[1].position,
            markedLocations[0].position.clampCompletionToCursor(1, mappingInfo.cursorMapper.sourceCursors[0], mappingInfo),
            Component.literal("Clamp to later line with cursor mapper")
        )

        context.succeed()
    }

    @GameTest
    fun testSemanticTokensOverlay(context: GameTestHelper) {
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

        context.assertValueEqual(expectedTokens.build().data, baseTokens.build().data, Component.literal("token data"))
        context.succeed()
    }

    @GameTest
    fun testSplitProcessedInputCursorMapperCombineWith(context: GameTestHelper) {
        val sourceMapper = SplitProcessedInputCursorMapper()
        val targetMapper = SplitProcessedInputCursorMapper()

        sourceMapper.addMapping(0, 0, 10)
        sourceMapper.addMapping(12, 10, 10)
        targetMapper.addMapping(0, 0, 8)
        targetMapper.addMapping(15, 8, 5)

        context.assertEqualsSnapshot(sourceMapper.combineWith(targetMapper), Component.literal("result"))

        val identityMapper = SplitProcessedInputCursorMapper()
        identityMapper.addMapping(0, 0, 10)
        identityMapper.addMapping(10, 10, 10)
        context.assertValueEqual(identityMapper.combineWith(identityMapper), identityMapper, Component.literal("combined identity mapper"))

        context.succeed()
    }

    @GameTest
    fun testAnalyzingResultAddOffset(context: GameTestHelper) {
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

            context.assertValueEqual(
                fullResult.getCompletionProviderForCursor(completionExamplePosition)!!.dataProvider(completionExamplePosition).get(),
                offsetResult.getCompletionProviderForCursor(completionExamplePosition)!!.dataProvider(completionExamplePosition).get(),
                Component.literal("completions from $description")
            )
            context.assertValueEqual(
                fullResult.diagnostics,
                offsetResult.diagnostics,
                Component.literal("diagnostics from $description")
            )
            context.assertValueEqual(
                fullResult.semanticTokens.build().data,
                offsetResult.semanticTokens.build().data,
                Component.literal("semantic tokens from $description")
            )
            context.assertValueEqual(
                fullResult.semanticTokens.lastLine,
                offsetResult.semanticTokens.lastLine,
                Component.literal("semantic tokens last line from $description")
            )
            context.assertValueEqual(
                fullResult.semanticTokens.lastCursor,
                offsetResult.semanticTokens.lastCursor,
                Component.literal("semantic tokens last cursor from $description")
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

        context.succeed()
    }

    @GameTest
    fun testGetInlineRangesBetweenCursors(context: GameTestHelper) {
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

            context.assertValueEqual(expected, result, Component.literal(description))
        }

        assertInlineRangesEquals(listOf(
            Range(markedLocations[0].position, markedLocations[1].position),
            Range(markedLocations[2].position, markedLocations[4].position),
            Range(markedLocations[5].position, markedLocations[6].position),
        ), markedLocations[0], markedLocations[6], "multiline")

        context.succeed()
    }

    @GameTest
    @OptIn(ExperimentalUnsignedTypes::class)
    fun testNodeMaxLiteralCounter(context: GameTestHelper) {
        val rootNode = RootCommandNode<SharedSuggestionProvider>()
        val command1 = LiteralArgumentBuilder.literal<SharedSuggestionProvider>("command1").build()
        rootNode.addChild(command1)
        rootNode.addChild(
            LiteralArgumentBuilder.literal<SharedSuggestionProvider>("command1")
                .then(LiteralArgumentBuilder.literal("just_a_leaf_node"))
                .then(LiteralArgumentBuilder.literal<SharedSuggestionProvider>("leading_up_to_a_loop")
                    .then(LiteralArgumentBuilder.literal<SharedSuggestionProvider>("looping_back")
                        .redirect(command1)
                    )
                ).build()
        )
        val command2 = LiteralArgumentBuilder.literal<SharedSuggestionProvider>("command2")
            .then(LiteralArgumentBuilder.literal("command2_sub"))
            .build()
        rootNode.addChild(command2)
        rootNode.addChild(
            LiteralArgumentBuilder.literal<SharedSuggestionProvider>("command2")
                .then(LiteralArgumentBuilder.literal<SharedSuggestionProvider>("command2_sub")
                    .then(LiteralArgumentBuilder.literal<SharedSuggestionProvider>("loop_back_to_command2_sub")
                        .redirect(command2.children.first())
                    ).then(LiteralArgumentBuilder.literal<SharedSuggestionProvider>("loop_back_to_root")
                        .redirect(rootNode)
                    )
                ).then(LiteralArgumentBuilder.literal("a_second_leaf_node"))
                .build()
        )
        val leafCommand = LiteralArgumentBuilder.literal<SharedSuggestionProvider>("leaf_command").build()
        rootNode.addChild(leafCommand)

        val nodeIdentifier = MacroAnalyzingCrawlerRunner.NodeIdentifier()
        nodeIdentifier.registerChildrenRecursive(rootNode)

        val maxLiteralCounter = MacroAnalyzingCrawlerRunner.NodeMaxLiteralCounter(nodeIdentifier)
        maxLiteralCounter.traverse(rootNode)

        fun assertLiteralCount(node: CommandNode<SharedSuggestionProvider>, literal: String, expectedCount: UByte) {
            val actualCount = maxLiteralCounter.getLiteralCountsForNode(node)[nodeIdentifier.getIdForLiteral(literal)]
            context.assertValueEqual(expectedCount, actualCount, Component.literal("Literal count for '$literal' in node '${node}'"))
        }

        assertLiteralCount(rootNode, "just_a_leaf_node", 1U)
        assertLiteralCount(command1, "just_a_leaf_node", 1U)
        assertLiteralCount(leafCommand, "just_a_leaf_node", 0U)

        assertLiteralCount(rootNode, "command1", 1U)
        assertLiteralCount(command1, "looping_back", 255U)

        assertLiteralCount(rootNode, "command2", 255U)
        assertLiteralCount(command2, "looping_back", 255U)

        assertLiteralCount(command2.children.first(), "a_second_leaf_node", 1U)

        // A node only counts child literals, but not itself
        assertLiteralCount(leafCommand, "leaf_command", 0U)
        assertLiteralCount(rootNode, "leaf_command", 1U)

        context.succeed()
    }

    @GameTest
    fun testCommandSuggestions(context: GameTestHelper) {
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
                Component.literal("Root suggestions for marker at index $rootIndex")
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
                Component.literal("Subcommand suggestions for marker at index $rootIndex")
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
                Component.literal("Predicate suggestions for marker at index $rootIndex")
            )
        }

        context.succeed()
    }

    @GameTest
    fun testMalformedPackratSuggestions(context: GameTestHelper) {
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
                Component.literal("Item predicate suggestions for marker at $i")
            )
        }

        context.succeed()
    }

    @GameTest
    fun testMultilineCommandsHighlighting(context: GameTestHelper) {
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
            context.assertValueEqual(expectedLineOffset,   tokenData[5 * tokenIndex + 0], Component.literal("Index $tokenIndex: token line"))
            context.assertValueEqual(expectedCursorOffset, tokenData[5 * tokenIndex + 1], Component.literal("Index $tokenIndex: token cursor"))
            context.assertValueEqual(expectedLength,       tokenData[5 * tokenIndex + 2], Component.literal("Index $tokenIndex: token length"))
        }

        testToken(0, 0, 0, 7)
        testToken(1, 1, 4, 3)
        testToken(2, 1, 4, 3)
        testToken(3, 0, 4, 3)
        testToken(4, 1, 0, 7)
        testToken(5, 0, 8, 3)
        testToken(6, 1, 1, 3)
        testToken(7, 0, 4, 3)
        context.succeed()
    }

    @GameTest
    fun parseTestFunction(context: GameTestHelper) {
        val lines = """
            # This is a doc \    
                comment
            execute as @a run say HI!
            execute if entity @s \ 
                run tp @s ~ ~1 ~
            @language vanilla improved
            execute
                align xyz
                run summon minecraft:armor_stand
            function {
                say HI!            
            }
        """.trimIndent().lines()
        try {
            testAllFunctionParsers(lines, context)
        } catch(e: CommandSyntaxException) {
            context.fail(Component.literal("Error testing function parsers: ${e.message}"))
        }
        context.succeed()
    }

    fun testAllFunctionParsers(lines: List<String>, context: GameTestHelper) {
        val id = Identifier.parse("test")
        @Suppress("UNCHECKED_CAST")
        val commandDispatcher = getCommandDispatcher(context)
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
        context.assertEqualsSnapshot(parsed.build(id), "parseToCommands_actions")

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
    fun testMacroHighlighting(context: GameTestHelper) {
        val lines = Files.readAllLines(inputDirectory.resolve("macros.mcfunction"))

        val result = analyseCommand(context, lines)
        context.assertEqualsSnapshot(result.semanticTokens.build().data, "semantic_tokens")
        context.succeed()
    }

    @GameTest
    fun testMacroSuggestions(context: GameTestHelper) {
        val markedLines = """
            ${'$'}execute $(sub) run §
            ${'$'}execute if entity $(selector) run §execute run execute run $(something)
            ${'$'}execute $(sub) §
            ${'$'}execute as @a at @s positioned $(Offset) unless entity @e[tag=a,distance=..0.1,gamemode=§] if entity @e[tag=$(anchor),distance=..10] run
        """.trimIndent().lines()
        val (processedLines, markedLocations) = getAndRemoveMarkedLocations(markedLines)
        val commandDispatcher = getCommandDispatcher(context)
        val result = analyseCommand(context, processedLines)

        val suggestions = markedLocations.map { location ->
            result.getCompletionProviderForCursor(location.absoluteCursor)
                ?.dataProvider?.invoke(location.absoluteCursor)?.get()
        }

        val line1Suggestions = suggestions[0]?.map { it.label }?.toSet()
        // Don't check for equality, because there will also be suggestions from `test run`.
        // But it shouldn't be a lot, so still check the count to make sure the only suggestions are
        // coming from `execute run` and `test run`, not from anywhere else
        context.assertTrue(
            line1Suggestions != null && line1Suggestions.containsAll(commandDispatcher.root.children.map { it.name }) && line1Suggestions.size < 200,
            "Expected line 1 suggestions to be root commands"
        )
        context.assertValueEqual(
            commandDispatcher.root.children.map { it.name }.toSet(),
            suggestions[1]?.map { it.label }?.toSet(),
            "line 2 root suggestions"
        )

        val line2Suggestions = suggestions[2]
        context.assertTrue(
            line2Suggestions != null && line2Suggestions.size > 1000, // Just some large number of suggestions
            "Expected line 3 suggestions to be everything, was not enough suggestions"
        )

        val line3Suggestions = suggestions[3]?.map { it.label }?.toSet()
        context.assertValueEqual(
            setOf("survival", "creative", "adventure", "spectator", "!survival", "!creative", "!adventure", "!spectator"),
            line3Suggestions,
            "line 4 selector gamemode suggestions"
        )

        context.succeed()
    }

    private fun analyseCommand(context: GameTestHelper, lines: List<String>): AnalyzingResult {
        val commandDispatcher = getCommandDispatcher(context)
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

    private fun getParsingCommandSource(context: GameTestHelper): CommandSourceStack =
        context.level.server!!.createCommandSourceStack()
            .withPosition(Vec3.ZERO) // Default position is the worldspawn, which changes between test so it must be set to another value

    @Suppress("UNCHECKED_CAST")
    private fun getCommandDispatcher(context: GameTestHelper): CommandDispatcher<SharedSuggestionProvider> =
        context.level.server!!.commands.dispatcher as CommandDispatcher<SharedSuggestionProvider>

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

    // Because Minecraft's doesn't allow null
    fun GameTestHelper.assertValueEqual(expected: Any?, actual: Any?, message: String) {
        if(expected != actual)
            throw this.assertionException("test.error.value_not_equal", message, expected ?: "null", actual ?: "null")
    }

    private data class FileLocation(val position: Position, val absoluteCursor: Int)
}