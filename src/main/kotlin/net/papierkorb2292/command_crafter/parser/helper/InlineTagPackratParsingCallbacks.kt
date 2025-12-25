package net.papierkorb2292.command_crafter.parser.helper

import com.mojang.brigadier.StringReader
import net.minecraft.util.parsing.packrat.Rule

interface InlineTagPackratParsingCallbacks<T: Any> {
    fun `command_crafter$getInlineTagRule`(): Rule<StringReader, T>
}