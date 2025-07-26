package net.papierkorb2292.command_crafter.test

import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.command.MacroInvocation
import net.minecraft.test.TestContext
import net.minecraft.text.Text
import net.papierkorb2292.command_crafter.helper.IntList
import net.papierkorb2292.command_crafter.helper.IntList.Companion.intListOf
import net.papierkorb2292.command_crafter.parser.helper.MacroCursorMapperProvider
import net.papierkorb2292.command_crafter.test.TestSnapshotHelper.assertEqualsSnapshot
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
    }
}