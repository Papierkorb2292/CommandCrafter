package net.papierkorb2292.command_crafter.editor.processing.string_range_tree

import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.TagParser
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultCreator
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.Language
import net.papierkorb2292.command_crafter.parser.helper.OffsetProcessedInputCursorMapper
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage
import org.eclipse.lsp4j.Position

data class NbtMacroParser constructor(private val parsingLanguage: VanillaLanguage?) : AnalyzingResourceCreator.MacroParser {
    companion object {
        private val parser = TagParser.create(NbtOps.INSTANCE)

        // Determine how to parse newlines in the string. But if there already is a parent macro parser, the string can be parsed like in vanilla
        private fun getLanguageForReader(originalReader: DirectiveStringReader<AnalyzingResourceCreator>) =
            if(originalReader.resourceCreator.macroParserStack.isEmpty() && originalReader.resourceCreator.macroQueue == null)
                originalReader.currentLanguage as? VanillaLanguage
                    ?: throw IllegalArgumentException("NbtMacroParser must be called with a VanillaLanguage reader")
            else null
    }

    constructor(originalReader: DirectiveStringReader<AnalyzingResourceCreator>): this(getLanguageForReader(originalReader))

    override fun parse(reader: DirectiveStringReader<AnalyzingResourceCreator>): AnalyzingResourceCreator.DecodedMacro? {
        val skippingCursor = reader.skippingCursor
        val startCursor = reader.cursor
        if(parsingLanguage != null) {
            reader.enterClosure(Language.TopLevelClosure(parsingLanguage))
            if(!parsingLanguage.easyNewLine) {
                // Parse escaped multiline
                reader.convertInputToEscapedMultiline()
                reader.peek() // Update mappings
                reader.disableTrimmingFromEscapedMultiline()
            }
        }
        val semanticTokensAnalyzingResult = AnalyzingResult(reader.fileMappingInfo, Position())
        (parser as AnalyzingResultCreator).`command_crafter$setAnalyzingResult`(semanticTokensAnalyzingResult)
        val parsed = try {
            parser.parseAsArgument(reader)
        } catch(_: CommandSyntaxException) {
            return null
        }
        val content = NbtStringContentGetter.getStringContent(parsed, reader.string, StringRange(startCursor, reader.cursor)) ?: return null
        content.cursorMapper.mapAllToTargetSorted(reader.resourceCreator.macroTargetCursors, false)
        val mappedContent = StringContent(
            content.content,
            OffsetProcessedInputCursorMapper(reader.fileMappingInfo.cursorMapper.mapToSource(skippingCursor))
                .combineWith(AnalyzingDynamicOps.buildCombinedStringMapper(reader.fileMappingInfo, content)),
            content.escaper
        )

        val macroTargetRange = StringRange(skippingCursor, reader.skippingCursor)
        return AnalyzingResourceCreator.DecodedMacro(mappedContent, reader.cursorMapper.mapToSource(macroTargetRange), semanticTokensAnalyzingResult.semanticTokens)
    }
}