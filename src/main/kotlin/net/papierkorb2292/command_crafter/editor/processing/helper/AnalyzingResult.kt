package net.papierkorb2292.command_crafter.editor.processing.helper

import net.papierkorb2292.command_crafter.editor.processing.SemanticTokensBuilder
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.Position

class AnalyzingResult(val semanticTokens: SemanticTokensBuilder = SemanticTokensBuilder(), val diagnostics: MutableList<Diagnostic> = mutableListOf()) {
    var completionsProvider: (Int) -> List<CompletionItem> = { emptyList() }

    companion object {
        fun getPositionFromCursor(cursor: Int, lines: List<String>): Position {
            var charactersLeft = cursor
            for((index, line) in lines.withIndex()) {
                val length = line.length + 1
                if(charactersLeft < length) {
                    return Position(index, charactersLeft)
                }
                charactersLeft -= length
            }
            return Position(lines.size, lines.last().length)
        }

        fun getInlineRangesBetweenCursors(startCursor: Int, endCursor: Int, lines: List<String>, rangeConsumer: (line: Int, cursor: Int, length: Int) -> Unit) {
            var startCharactersLeft = startCursor
            var line = 0
            while(lines.size > line && startCharactersLeft >= lines[line].length + 1) {
                startCharactersLeft -= lines[line++].length + 1
            }
            var rangeCharactersLeft = endCursor - startCursor
            while(lines.size > line && rangeCharactersLeft > 0) {
                val lineLength = lines[line].length + 1
                if(lineLength >= rangeCharactersLeft) {
                    rangeConsumer(line, startCharactersLeft, rangeCharactersLeft)
                    return
                }
                rangeConsumer(line, startCharactersLeft, lineLength)
                line++
                startCharactersLeft = 0
                rangeCharactersLeft -= lineLength
            }
        }
    }
}