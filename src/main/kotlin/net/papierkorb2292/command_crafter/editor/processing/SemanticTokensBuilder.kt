package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.context.StringRange
import net.papierkorb2292.command_crafter.helper.binarySearch
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import org.eclipse.lsp4j.SemanticTokens
import kotlin.math.min

class SemanticTokensBuilder(val reader: DirectiveStringReader<*>) {
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

    fun addMultiline(
        cursor: Int,
        length: Int,
        type: TokenType,
        modifiers: Int
    ) {
        val lines = reader.lines
        // Find the starting line
        if(lines.isEmpty())
            return
        val offsetCursor = cursor + reader.readSkippingChars
        val cursorMapper = reader.cursorMapper
        // Map the command cursor to an absolute cursor
        var mappingIndex = cursorMapper.targetCursors.binarySearch { index ->
            if(cursorMapper.targetCursors[index] <= offsetCursor) -1
            else if (cursorMapper.targetCursors[index] + cursorMapper.lengths[index] > offsetCursor) 1
            else 0
        }
        if(mappingIndex < 0) {
            mappingIndex = -(mappingIndex + 2)
        }
        var mappingRelativeCursor = offsetCursor
        if(mappingIndex >= 0) {
            mappingRelativeCursor -= cursorMapper.targetCursors[mappingIndex]
        }

        // Get the corresponding line for the absolute cursor
        var lineNumber = 0
        var remainingLineLength = lines[lineNumber].length + 1 //Account for '\n'

        // Distribute the length over the mapped regions and convert the regions that are covered by the length to semantic tokens (a region might include multiple lines)
        var remainingLength = length
        var prevMappingAbsoluteStart = 0
        while(remainingLength > 0 && mappingIndex < cursorMapper.targetCursors.size()) {
            var remainingLengthCoveredByMapping =
                if(mappingIndex >= 0 && mappingRelativeCursor <= cursorMapper.lengths[mappingIndex])
                    min(remainingLength, cursorMapper.lengths[mappingIndex] - mappingRelativeCursor)
                else
                    remainingLength
            remainingLength -= remainingLengthCoveredByMapping

            val mappingAbsoluteStart =
                if(mappingIndex >= 0) cursorMapper.sourceCursors[mappingIndex] + mappingRelativeCursor
                else mappingRelativeCursor
            var cursorDelta = mappingAbsoluteStart - prevMappingAbsoluteStart
            prevMappingAbsoluteStart = mappingAbsoluteStart

            while(cursorDelta >= remainingLineLength) {
                cursorDelta -= remainingLineLength
                if(++lineNumber >= lines.size)
                    return
                remainingLineLength = lines[lineNumber].length + 1
            }
            remainingLineLength -= cursorDelta

            // Go through the lines that the mapping covers and add semantic tokens
            while(remainingLengthCoveredByMapping > 0) {
                if(remainingLengthCoveredByMapping <= remainingLineLength) {
                    add(lineNumber, cursorDelta, remainingLengthCoveredByMapping, type, modifiers)
                    //remainingLineLength -= remainingLengthCoveredByMapping
                    break
                }
                val sectionLength = remainingLineLength
                add(lineNumber, cursorDelta, sectionLength, type, modifiers)

                if(++lineNumber >= lines.size) return
                remainingLineLength = lines[lineNumber].length + 1
                remainingLengthCoveredByMapping -= sectionLength
                cursorDelta = 0
            }

            mappingRelativeCursor = 0
            mappingIndex++
        }
    }

    fun addMultiline(range: StringRange, type: TokenType, modifiers: Int) {
        addMultiline(range.start, range.length, type, modifiers)
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