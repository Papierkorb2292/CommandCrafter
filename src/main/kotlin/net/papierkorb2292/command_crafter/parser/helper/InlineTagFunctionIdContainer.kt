package net.papierkorb2292.command_crafter.parser.helper

import net.minecraft.util.Identifier

interface InlineTagFunctionIdContainer {
    fun `command_crafter$getInlineTagFunctionId`(): Identifier?
    fun `command_crafter$setInlineTagFunctionId`(id: Identifier)
}