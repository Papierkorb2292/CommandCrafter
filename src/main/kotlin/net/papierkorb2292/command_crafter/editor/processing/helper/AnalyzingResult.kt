package net.papierkorb2292.command_crafter.editor.processing.helper

import com.mojang.brigadier.context.StringRange
import net.papierkorb2292.command_crafter.editor.processing.SemanticTokensBuilder
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.Position

class AnalyzingResult(val semanticTokens: SemanticTokensBuilder, val diagnostics: MutableList<Diagnostic> = mutableListOf()) {
    constructor(lines: List<String>, diagnostics: MutableList<Diagnostic> = mutableListOf()) : this(SemanticTokensBuilder(lines), diagnostics)

    var completionProviders: MutableList<CompletionProvider> = mutableListOf()

    fun combineWith(other: AnalyzingResult) {
        semanticTokens.combineWith(other.semanticTokens)
        diagnostics += other.diagnostics
        completionProviders += other.completionProviders
    }

    companion object {
        fun getPositionFromCursor(cursor: Int, lines: List<String>, zeroBased: Boolean = true): Position {
            var charactersLeft = cursor
            for((index, line) in lines.withIndex()) {
                val length = line.length + 1
                if(charactersLeft < length) {
                    return if(zeroBased) Position(index, charactersLeft)
                        else Position(index + 1, charactersLeft + 1)
                }
                charactersLeft -= length
            }
            return Position(lines.size, lines.last().length)
        }

        fun getCursorFromPosition(lines: List<String>, position: Position, zeroBased: Boolean = true): Int {
            var cursor = 0
            val lineIndex = if(zeroBased) position.line else position.line - 1
            for((index, line) in lines.withIndex()) {
                if(index == lineIndex) {
                    return cursor + position.character
                }
                cursor += line.length + 1
            }
            return cursor
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

        fun getLineCursorRange(lineNumber: Int, lines: List<String>): StringRange {
            var cursor = 0
            val lineIndex = lineNumber - 1
            for((index, currentText) in lines.withIndex()) {
                if(index == lineIndex) {
                    return StringRange(cursor, cursor + currentText.length)
                }
                cursor += currentText.length + 1
            }
            return StringRange(cursor, cursor)
        }
    }

    class CompletionProvider(cursorStart: Int, cursorEnd: Int, completionCreator: (Int) -> List<CompletionItem>)
}