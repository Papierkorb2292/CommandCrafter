package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.context.StringRange
import org.eclipse.lsp4j.SemanticTokens

class SemanticTokensBuilder(private val lines: List<String>) {
    private val data = ArrayList<Int>(100)
    private var dataSize = 100
    private var lastLine = 0
    private var lastCursor = 0

    fun add(line: Int, cursor: Int, length: Int, type: TokenType, modifiers: Int) {
        add(line, cursor, length, type.id, modifiers)
    }

    private fun add(line: Int, cursor: Int, length: Int, typeId: Int, modifiers: Int) {
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
        data.add(typeId)
        data.add(modifiers)
        lastCursor = cursor
    }

    fun addAbsoluteMultiline(
        absoluteCursor: Int,
        length: Int,
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
            if(++lineNumber >= lines.size)
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

    fun addAbsoluteMultiline(range: StringRange, type: TokenType, modifiers: Int) {
        addAbsoluteMultiline(range.start, range.length, type, modifiers)
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

    fun combineWith(other: SemanticTokensBuilder) {
        dataSize += other.dataSize
        data.ensureCapacity(dataSize)

        // The line and cursor of the other's first entry must be made
        // relative to the last token of this builder
        if(other.data.size < 5)
            return
        add(other.data[0], other.data[1], other.data[2], other.data[3], other.data[4])

        // The rest of the tokens are already relative to the previous one
        for(value in other.data.subList(5, other.data.size)) {
            data += value
        }

        lastLine = other.lastLine
        lastCursor = other.lastCursor
    }

    fun build() = SemanticTokens(data)
}