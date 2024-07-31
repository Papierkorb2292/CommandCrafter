package net.papierkorb2292.command_crafter.editor.debugger.helper

import com.mojang.brigadier.context.StringRange

interface StringRangeContainer {
    fun `command_crafter$setRange`(range: StringRange)
    fun `command_crafter$getRange`(): StringRange?
}