package net.papierkorb2292.command_crafter.test

import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.test.TestContext
import net.minecraft.text.Text
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
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
}