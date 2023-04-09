package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.context.StringRange
import org.eclipse.lsp4j.SemanticTokens

class SemanticTokensBuilder {
    private val data = ArrayList<Int>(100)
    private var dataSize = 100
    private var lastLine = 0
    private var lastCursor = 0

    fun add(line: Int, cursor: Int, length: Int, type: TokenType, modifiers: Int) {
        if(data.size >= dataSize) {
            dataSize += 100
            data.ensureCapacity(dataSize)
        }
        data.add(line - lastLine)
        if(lastLine != line) {
            lastLine = line
            lastCursor = 0
            data.add(cursor)
        } else {
            data.add(cursor - lastCursor)
        }
        data.add(length)
        data.add(type.id)
        data.add(modifiers)
        lastCursor = cursor
    }

    fun addAbsoluteMultiline(
        absoluteCursor: Int,
        length: Int,
        lines: List<String>,
        type: TokenType,
        modifiers: Int
    ) {
        // Find the starting line
        if(lines.isEmpty())
            return
        var cursor = absoluteCursor
        var lineNumber = 0
        var lineLength = lines[lineNumber].length + 1 //Account for '\n'
        while(cursor >= lineLength){
            cursor -= lineLength
            if(++lineNumber > lines.size)
                return //The specified region is outside the reader
            lineLength = lines[lineNumber].length + 1
        }

        // Add the corresponding semantic tokens
        var remainingLength = length
        while(true) {
            if(cursor + remainingLength <= lineLength) {
                add(lineNumber, cursor, remainingLength, type, modifiers)
                break
            }
            val sectionLength = lineLength - cursor
            add(lineNumber, cursor, sectionLength, type, modifiers)
            remainingLength -= sectionLength

            if(++lineNumber > lines.size) break
            lineLength = lines[lineNumber].length
        }
    }

    fun addAbsoluteMultiline(range: StringRange, lines: List<String>, type: TokenType, modifiers: Int) {
        addAbsoluteMultiline(range.start, range.length, lines, type, modifiers)
    }

    fun addRelative(lineDelta: Int, cursorDelta: Int, length: Int, type: TokenType, modifiers: Int) {
        if(data.size >= dataSize) {
            dataSize += 100
            data.ensureCapacity(dataSize)
        }
        data.add(lineDelta)
        lastLine += lineDelta
        data.add(cursorDelta)
        if(lineDelta == 0) {
            lastCursor = cursorDelta
        } else {
            lastCursor += cursorDelta
        }
        data.add(length)
        data.add(type.id)
        data.add(modifiers)
    }

    fun fill(tokens: SemanticTokens, resultId: String? = null) {
        tokens.data = data
        if(resultId != null)
            tokens.resultId = resultId
    }
}