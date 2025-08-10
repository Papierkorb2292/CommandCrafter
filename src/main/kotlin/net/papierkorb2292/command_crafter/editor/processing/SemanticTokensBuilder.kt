package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.context.StringRange
import net.papierkorb2292.command_crafter.helper.binarySearch
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import org.eclipse.lsp4j.SemanticTokens
import kotlin.math.min

class SemanticTokensBuilder(val mappingInfo: FileMappingInfo) {
    private val data = ArrayList<Int>(100)
    private var lastLine = 0
    private var lastCursor = 0

    fun add(line: Int, cursor: Int, length: Int, type: TokenType, modifiers: Int) {
        add(line, cursor, length, type.id, modifiers)
    }

    private fun add(line: Int, cursor: Int, length: Int, typeId: Int, modifiers: Int) {
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
        val lines = mappingInfo.lines
        // Find the starting line
        if(lines.isEmpty())
            return
        val offsetCursor = cursor + mappingInfo.readSkippingChars
        val cursorMapper = mappingInfo.cursorMapper
        // Map the command cursor to an absolute cursor
        var mappingIndex = cursorMapper.targetCursors.binarySearch { index ->
            if(cursorMapper.targetCursors[index] + cursorMapper.lengths[index] <= offsetCursor) -1
            else if (cursorMapper.targetCursors[index] > offsetCursor) 1
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
        var lastLineCursor = 0
        while(remainingLength > 0 && mappingIndex < cursorMapper.targetCursors.size) {
            var remainingLengthCoveredByMapping =
                if(mappingIndex < 0)
                    remainingLength
                else if(mappingRelativeCursor < cursorMapper.lengths[mappingIndex])
                    min(remainingLength, cursorMapper.lengths[mappingIndex] - mappingRelativeCursor)
                else if(mappingIndex == cursorMapper.targetCursors.size - 1)
                    remainingLength
                else {
                    // Distance to start of next mapping
                    min(remainingLength, cursorMapper.targetCursors[mappingIndex + 1] - cursorMapper.targetCursors[mappingIndex] - mappingRelativeCursor)
                }
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
                lastLineCursor = 0
            }
            remainingLineLength -= cursorDelta

            val mappingAbsoluteEndExclusive = mappingAbsoluteStart + remainingLengthCoveredByMapping
            val mappingAbsoluteEndInclusive = mappingAbsoluteEndExclusive - 1
            if(cursorMapper.expandedCharEnds.containsKey(mappingAbsoluteEndInclusive)) {
                val expandedCharEndInclusive = cursorMapper.expandedCharEnds[mappingAbsoluteEndInclusive]
                remainingLengthCoveredByMapping = expandedCharEndInclusive + 1 - mappingAbsoluteStart
            }

            // Go through the lines that the mapping covers and add semantic tokens
            while(remainingLengthCoveredByMapping > 0) {
                lastLineCursor += cursorDelta
                if(remainingLengthCoveredByMapping <= remainingLineLength) {
                    add(lineNumber, lastLineCursor, remainingLengthCoveredByMapping, type, modifiers)
                    //remainingLineLength -= remainingLengthCoveredByMapping
                    break
                }
                val sectionLength = remainingLineLength
                add(lineNumber, lastLineCursor, sectionLength, type, modifiers)

                if(++lineNumber >= lines.size) return
                remainingLineLength = lines[lineNumber].length + 1
                remainingLengthCoveredByMapping -= sectionLength
                lastLineCursor = 0
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

    /**
     * Overlaps the SemanticTokensBuilders onto this SementicTokensBuilder,
     * meaning the tokens from the sortedOverlaps will be added to this builder starting at
     * the beginning of the file and split up existing tokens where necessary.
     */
    fun overlap(sortedOverlaps: Iterator<SemanticTokensBuilder>) {
        var currentTokenIndex = 0
        lastLine = 0
        lastCursor = 0
        for(overlap in sortedOverlaps) {
            var srcLine = 0
            var srcCursor = 0
            srcTokens@for(i in 0 until overlap.data.size step 5) {
                srcLine += overlap.data[i]
                srcCursor = overlap.data[i + 1] + if(overlap.data[i] == 0) srcCursor else 0
                val srcLength = overlap.data[i + 2]
                val srcTypeId = overlap.data[i + 3]
                val srcModifiers = overlap.data[i + 4]

                while(currentTokenIndex < data.size) {
                    val newDestLine = lastLine + data[currentTokenIndex]
                    val newDestCursor = data[currentTokenIndex + 1] + if(data[currentTokenIndex] == 0) lastCursor else 0
                    val destLength = data[currentTokenIndex + 2]
                    val destTypeId = data[currentTokenIndex + 3]
                    val destModifiers = data[currentTokenIndex + 4]

                    if(newDestLine > srcLine || (newDestLine == srcLine && newDestCursor + destLength > srcCursor)) {
                        // dest token is not completely before src token

                        if(newDestLine > srcLine || newDestCursor > srcCursor + srcLength) {
                            // dest token is completely after src token, add src token to the result and move to the next src token
                            data.add(currentTokenIndex, srcLine - lastLine)
                            data.add(currentTokenIndex + 1, if(srcLine == lastLine) srcCursor - lastCursor else srcCursor)
                            data.add(currentTokenIndex + 2, srcLength)
                            data.add(currentTokenIndex + 3, srcTypeId)
                            data.add(currentTokenIndex + 4, srcModifiers)

                            lastLine = srcLine
                            lastCursor = srcCursor

                            currentTokenIndex += 5
                            data[currentTokenIndex] = newDestLine - srcLine
                            data[currentTokenIndex + 1] = if(newDestLine == srcLine) newDestCursor - srcCursor else newDestCursor
                            continue@srcTokens
                        }

                        // Tokens must be on the same line and overlap
                        val cursorDiff: Int

                        if(newDestCursor < srcCursor) {
                            // Src token doesn't cover start of dest token, so trim dest token length
                            cursorDiff = srcCursor - newDestCursor
                            data[currentTokenIndex + 2] = cursorDiff
                            currentTokenIndex += 5
                        } else {
                            // Token is removed, becaise its start is covered by the src token. The remaining part of the dest token will be added back later
                            data.subList(currentTokenIndex, currentTokenIndex + 5).clear()
                            // Use previous destCursor because the token that starts at newDestCursor has been removed
                            cursorDiff = srcCursor - lastCursor
                        }

                        data.add(currentTokenIndex, 0)
                        data.add(currentTokenIndex + 1, cursorDiff)
                        data.add(currentTokenIndex + 2, srcLength)
                        data.add(currentTokenIndex + 3, srcTypeId)
                        data.add(currentTokenIndex + 4, srcModifiers)

                        val hasRemainingDest = newDestCursor + destLength > srcCursor + srcLength

                        if(hasRemainingDest) {
                            // The dest token is longer than the src token, add its remaining part
                            currentTokenIndex += 5
                            val remainingLength = newDestCursor + destLength - srcCursor - srcLength
                            data.add(currentTokenIndex, 0)
                            data.add(currentTokenIndex + 1, srcLength)
                            data.add(currentTokenIndex + 2, remainingLength)
                            data.add(currentTokenIndex + 3, destTypeId)
                            data.add(currentTokenIndex + 4, destModifiers)

                            // Set position to src, because the src token is going to be the previous token when reading the remaining part of the dest token
                            // that has just been placed at currentTokenIndex
                            lastLine = srcLine
                            lastCursor = srcCursor
                        } else {
                            // Set position to dest, because the dest token is going to be the previous token when reading the src token that has just been placed at currentTokenIndex
                            lastLine = newDestLine
                            lastCursor = newDestCursor
                        }

                        if(currentTokenIndex + 5 < data.size && data[currentTokenIndex + 5] == 0) {
                            // Adjust position of next token cursor, which will require a different offset now

                            // Always use the cursorDiff between the current src token and dest token instead of the previous dest token (which is normally used when src token covers the start of dest token)
                            // Because the next token position is also relative to the current dest token, not the previous one.
                            val originalCursorDiff = srcCursor - newDestCursor

                            data[currentTokenIndex + 6] -= if(hasRemainingDest) originalCursorDiff + srcLength else originalCursorDiff
                        }
                        continue@srcTokens
                    }

                    lastLine = newDestLine
                    lastCursor = newDestCursor
                    currentTokenIndex += 5
                }

                // There are no dest tokens left, add src token at the end
                add(srcLine, srcCursor, srcLength, srcTypeId, srcModifiers)
                currentTokenIndex += 5
            }
        }

        // Restore lastLine and lastCursor
        while(currentTokenIndex < data.size) {
            lastLine += data[currentTokenIndex]
            lastCursor = (if(data[currentTokenIndex] == 0) lastCursor else 0) + data[currentTokenIndex + 1]
            currentTokenIndex += 5
        }
    }

    fun clear() {
        data.clear()
        lastLine = 0
        lastCursor = 0
    }

    fun isEmpty() = data.isEmpty()

    fun build() = SemanticTokens(data)
}