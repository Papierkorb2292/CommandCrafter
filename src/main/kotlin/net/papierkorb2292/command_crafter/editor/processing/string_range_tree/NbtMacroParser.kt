package net.papierkorb2292.command_crafter.editor.processing.string_range_tree

import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.TagParser
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.helper.OffsetProcessedInputCursorMapper

object NbtMacroParser : AnalyzingResourceCreator.MacroParser {
    private val parser = TagParser.create(NbtOps.INSTANCE)

    override fun parse(reader: DirectiveStringReader<AnalyzingResourceCreator>): AnalyzingResourceCreator.DecodedMacro? {
        val absoluteCursor = reader.absoluteCursor
        val skippingCursor = reader.skippingCursor
        val startCursor = reader.cursor
        val parsed = try {
            parser.parseFully(reader)
        } catch(_: CommandSyntaxException) {
            return null
        }
        val content = NbtStringContentGetter.getStringContent(parsed, reader.string, StringRange(startCursor, reader.cursor)) ?: return null
        val mappedContent = StringContent(
            content.content,
            OffsetProcessedInputCursorMapper(absoluteCursor)
                .combineWith(content.cursorMapper)
                .combineWith(OffsetProcessedInputCursorMapper(-skippingCursor)),
            content.escaper
        )

        val macroTargetRange = StringRange(skippingCursor, reader.skippingCursor)
        return AnalyzingResourceCreator.DecodedMacro(mappedContent, reader.cursorMapper.mapToSource(macroTargetRange))
    }
}