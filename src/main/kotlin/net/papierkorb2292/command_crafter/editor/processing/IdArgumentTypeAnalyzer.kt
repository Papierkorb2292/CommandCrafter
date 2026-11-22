package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.context.StringRange
import net.minecraft.resources.Identifier
import net.papierkorb2292.command_crafter.editor.processing.TokenType.Companion.PARAMETER
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs
import net.papierkorb2292.command_crafter.editor.processing.symbols.SymbolKind
import net.papierkorb2292.command_crafter.editor.processing.symbols.SymbolLocationMap
import net.papierkorb2292.command_crafter.editor.processing.symbols.SymbolNode
import net.papierkorb2292.command_crafter.helper.getOrNull
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader

object IdArgumentTypeAnalyzer {
    fun analyzeForId(id: Identifier, packContentFileType: PackContentFileType, range: StringRange, result: AnalyzingResult, reader: DirectiveStringReader<AnalyzingResourceCreator>) {
        result.semanticTokens.addMultiline(range, PARAMETER, 0)
        val fileRange = result.toFileRange(range)
        //TODO: Pass symbol kind as argument
        val symbolNode = SymbolNode(packContentFileType, id, SymbolKind.Reference, SymbolLocationMap.SymbolLocation(fileRange, null, null))
        result.addSymbol(symbolNode)
        result.addMappedActualSyntaxNode(range, symbolNode.getActualSyntaxNode(reader.resourceCreator))
    }

    fun analyzePackrat(id: Identifier, start: Int, reader: StringReader, fileType: PackContentFileType?) {
        val analyzingResultArg = PackratParserAdditionalArgs.analyzingResult.getOrNull()
        if(analyzingResultArg != null && reader is DirectiveStringReader<*> && reader.resourceCreator is AnalyzingResourceCreator) {
            val range = StringRange(start, reader.cursor)
            if(fileType != null) {
                @Suppress("UNCHECKED_CAST")
                analyzeForId(
                    id,
                    fileType,
                    range,
                    analyzingResultArg.analyzingResult,
                    reader as DirectiveStringReader<AnalyzingResourceCreator>
                )
            } else {
                analyzingResultArg.analyzingResult.semanticTokens.addMultiline(range, PARAMETER, 0)
            }
        }
    }
}