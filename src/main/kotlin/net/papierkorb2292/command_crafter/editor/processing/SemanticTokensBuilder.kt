package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.context.StringRange
import net.papierkorb2292.command_crafter.parser.helper.ProcessedInputCursorMapper
import org.eclipse.lsp4j.SemanticTokens
import kotlin.math.min

class SemanticTokensBuilder(private val lines: List<String>) {
    var cursorOffset = 0
    var cursorMapper: ProcessedInputCursorMapper? = null

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
        var offsetCursor = absoluteCursor + cursorOffset
        val cursorMapper = cursorMapper
        if(cursorMapper == null) {
            var lineNumber = 0
            var lineLength = lines[lineNumber].length + 1 //Account for '\n'
            while(offsetCursor >= lineLength){
                offsetCursor -= lineLength
                if(++lineNumber >= lines.size)
                    return //The specified region is outside the reader
                lineLength = lines[lineNumber].length + 1
            }

            // Add the corresponding semantic tokens
            var remainingLength = length
            while(true) {
                if(offsetCursor + remainingLength <= lineLength) {
                    add(lineNumber, offsetCursor, remainingLength, type, modifiers)
                    break
                }
                val sectionLength = lineLength - offsetCursor
                add(lineNumber, offsetCursor, sectionLength, type, modifiers)
                remainingLength -= sectionLength

                if(++lineNumber >= lines.size) break
                lineLength = lines[lineNumber].length
                offsetCursor = 0
            }
            return
        }
        // Map the command cursor to an absolute cursor
        var nextMappingIndex = 0
        var mappingRelativeCursor = 0
        while(nextMappingIndex < cursorMapper.targetCursors.size()) {
            if(cursorMapper.targetCursors[nextMappingIndex] > offsetCursor) {
                if(nextMappingIndex == 0) return
                mappingRelativeCursor = min(
                    cursorMapper.lengths[nextMappingIndex - 1],
                    offsetCursor - cursorMapper.targetCursors[nextMappingIndex - 1]
                )
                break
            }
            nextMappingIndex++
        }
        if(nextMappingIndex == cursorMapper.targetCursors.size()) {
            val lastIndex = cursorMapper.sourceCursors.size() - 1
            mappingRelativeCursor = min(
                cursorMapper.lengths[lastIndex],
                offsetCursor - cursorMapper.targetCursors[lastIndex]
            )
        }
        var mappingIndex = nextMappingIndex - 1

        // Get the corresponding line for the absolute cursor
        var lineNumber = 0
        var remainingLineLength = lines[lineNumber].length + 1 //Account for '\n'

        // Distribute the length over the mapped regions and convert the regions that are covered by the length to semantic tokens (a region might include multiple lines)
        var remainingLength = length
        var prevMappingAbsoluteStart = 0
        while(remainingLength > 0 && mappingIndex < cursorMapper.targetCursors.size()) {
            var remainingLengthCoveredByMapping = min(remainingLength, cursorMapper.lengths[mappingIndex] - mappingRelativeCursor)
            remainingLength -= remainingLengthCoveredByMapping

            val mappingAbsoluteStart = cursorMapper.sourceCursors[mappingIndex] + mappingRelativeCursor
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