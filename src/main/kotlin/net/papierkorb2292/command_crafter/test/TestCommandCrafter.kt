package net.papierkorb2292.command_crafter.test

import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.test.TestContext
import net.minecraft.text.Text

object TestCommandCrafter {
    @GameTest
    fun exampleTest(context: TestContext) {
        context.assertEquals(1, 3, Text.literal("example"))
        context.complete()
    }
}