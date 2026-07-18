package net.papierkorb2292.command_crafter.editor.processing.string_range_tree

import com.google.gson.JsonParseException
import com.mojang.brigadier.context.StringRange
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.helper.OffsetProcessedInputCursorMapper
import net.papierkorb2292.command_crafter.string_range_gson.JsonReader
import net.papierkorb2292.command_crafter.string_range_gson.Strictness
import java.io.StringReader

object JsonMacroParser : AnalyzingResourceCreator.MacroParser {
    override fun parse(reader: DirectiveStringReader<AnalyzingResourceCreator>): AnalyzingResourceCreator.DecodedMacro? {
        val absoluteCursor = reader.absoluteCursor
        val skippingCursor = reader.skippingCursor
        val sourceLines = StringBuilder(reader.readString())
        while(reader.canRead()) {
            sourceLines.append('\n')
            sourceLines.append(reader.readLine())
        }
        val input = sourceLines.toString()
        val parsed = try {
            StringRangeTreeJsonReader { JsonReader(StringReader(input)) }.read(Strictness.STRICT)
        } catch(_: JsonParseException) {
            return null
        }
        val content = StringRangeTreeJsonReader.StringContentGetter(parsed, input).getStringContent(parsed.root) ?: return null
        val mappedContent = StringContent(
            content.content,
            OffsetProcessedInputCursorMapper(absoluteCursor)
                .combineWith(content.cursorMapper)
                .combineWith(OffsetProcessedInputCursorMapper(-skippingCursor)),
            content.escaper
        )

        val rangeInParent = StringRange(skippingCursor, reader.skippingCursor)
        return AnalyzingResourceCreator.DecodedMacro(mappedContent, reader.cursorMapper.mapToSource(rangeInParent), rangeInParent)
    }
}