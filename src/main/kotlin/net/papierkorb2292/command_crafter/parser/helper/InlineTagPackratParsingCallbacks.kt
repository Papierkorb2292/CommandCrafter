package net.papierkorb2292.command_crafter.parser.helper

import com.mojang.brigadier.StringReader
import net.minecraft.util.packrat.ParsingRule

interface InlineTagPackratParsingCallbacks<T> {
    fun `command_crafter$getInlineTagRule`(): ParsingRule<StringReader, T>
}