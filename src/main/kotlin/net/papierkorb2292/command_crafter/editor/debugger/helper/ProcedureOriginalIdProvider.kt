package net.papierkorb2292.command_crafter.editor.debugger.helper

import net.minecraft.resources.Identifier

interface ProcedureOriginalIdContainer {
    fun `command_crafter$getOriginalId`(): Identifier
    fun `command_crafter$setOriginalId`(id: Identifier)
}