package net.papierkorb2292.command_crafter.test

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.command.MacroInvocation
import net.papierkorb2292.command_crafter.helper.IntList.Companion.intListOf
import net.papierkorb2292.command_crafter.parser.helper.MacroCursorMapperProvider
import net.minecraft.command.CommandSource
import net.minecraft.test.TestContext
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
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
import org.eclipse.lsp4j.Position
import org.spongepowered.asm.mixin.MixinEnvironment

object TestCommandCrafter {
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
    fun testMacroInvocationCursorMapper(context: TestContext) {
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
    fun parseTestFunction(context: TestContext) {
        val lines = """
            # This is a doc \    
                comment
            execute as @a run say HI!
            execute if entity @s \ 
                run tp @s ~ ~1 ~
            ${'$'}say hi to $(the_macro) \  
                for me
            @language vanilla improved
            execute
                align xyz run
                summon minecraft:armor_stand 
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
        val source = context.world.server.commandSource
            .withPosition(Vec3d.ZERO) // Default position is the worldspawn, which changes between test so it must be set to another value

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
}