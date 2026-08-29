package net.papierkorb2292.command_crafter.editor.processing.string_range_tree

import com.google.gson.JsonParseException
import com.mojang.brigadier.context.StringRange
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.helper.OffsetProcessedInputCursorMapper
import net.papierkorb2292.command_crafter.string_range_gson.JsonReader
import net.papierkorb2292.command_crafter.string_range_gson.Strictness
import java.io.IOException

object JsonMacroParser : AnalyzingResourceCreator.MacroParser {
    override fun parse(reader: DirectiveStringReader<AnalyzingResourceCreator>): AnalyzingResourceCreator.DecodedMacro? {
        val startCursor = reader.cursor
        val skippingCursor = reader.skippingCursor
        val parsed = try {
            StringRangeTreeJsonReader { JsonReader(reader.asReader()) }.read(Strictness.STRICT)
        } catch(_: JsonParseException) {
            return null
        } catch(_: IOException) {
            return null
        }
        val length = parsed.ranges[parsed.root]!!.end
        reader.cursor = startCursor + length
        val content = StringRangeTreeJsonReader.StringContentGetter(parsed, reader.string.substring(startCursor, reader.cursor)).getStringContent(parsed.root) ?: return null
        content.cursorMapper.mapAllToTargetSorted(reader.resourceCreator.macroTargetCursors, true)
        val mappedContent = StringContent(
            content.content,
            OffsetProcessedInputCursorMapper(reader.fileMappingInfo.cursorMapper.mapToSource(skippingCursor) - startCursor) // Subtract start cursor here, because it is the offset that is missing from the node's StringRange
                .combineWith(AnalyzingDynamicOps.buildCombinedStringMapper(reader.fileMappingInfo, content)),
            content.escaper
        )

        val macroTargetRange = StringRange(skippingCursor, reader.skippingCursor)
        return AnalyzingResourceCreator.DecodedMacro(mappedContent, reader.cursorMapper.mapToSource(macroTargetRange))
    }
}