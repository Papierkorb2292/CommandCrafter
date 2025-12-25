package net.papierkorb2292.command_crafter.parser.helper

import net.minecraft.resources.Identifier

interface InlineTagFunctionIdContainer {
    fun `command_crafter$getInlineTagFunctionId`(): Identifier?
    fun `command_crafter$setInlineTagFunctionId`(id: Identifier)
}