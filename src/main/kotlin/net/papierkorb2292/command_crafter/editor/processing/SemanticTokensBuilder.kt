package net.papierkorb2292.command_crafter.editor.processing

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.mojang.brigadier.context.StringRange
import net.papierkorb2292.command_crafter.editor.processing.helper.advance
import net.papierkorb2292.command_crafter.editor.processing.helper.compareTo
import net.papierkorb2292.command_crafter.editor.processing.helper.differenceTo
import net.papierkorb2292.command_crafter.editor.processing.helper.offsetBy
import net.papierkorb2292.command_crafter.helper.binarySearch
import net.papierkorb2292.command_crafter.helper.roundDownBinarySearch
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SemanticTokens
import kotlin.math.min

class SemanticTokensBuilder(val mappingInfo: FileMappingInfo) {
    /**
     * List of all semantic tokens. Each semantic token is represented by 5 integers: line delta, cursor delta, length, type id, modifiers.
     * Notably, cursor delta is relative to the previous token position only if line delta is 0, otherwise it's relative to the start of the line.
     * Also, each token in this list never covers multiple lines, only one.
     */
    private val data = ArrayList<Int>(100)
    var lastLine = 0
        private set
    var lastCursor = 0
        private set
    var multilineTokenCount = 0
        private set

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
        multilineTokenCount++
        val lines = mappingInfo.lines
        // Find the starting line
        if(lines.isEmpty())
            return
        val offsetCursor = cursor + mappingInfo.readSkippingChars
        val cursorMapper = mappingInfo.cursorMapper
        // Map the command cursor to an absolute cursor
        var mappingIndex = roundDownBinarySearch(cursorMapper.targetCursors.binarySearch(offsetCursor))

        // Multiple mappings might have the same start (length can be 0), this method selects the last one
        while(mappingIndex + 1 < cursorMapper.targetCursors.size && cursorMapper.targetCursors[mappingIndex + 1] == offsetCursor)
            mappingIndex++

        var mappingRelativeCursor = offsetCursor
        if(mappingIndex >= 0)
            mappingRelativeCursor -= cursorMapper.targetCursors[mappingIndex]

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
            lastLineCursor += cursorDelta
            while(remainingLengthCoveredByMapping > 0) {
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
        multilineTokenCount += other.multilineTokenCount
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
    fun overlay(sortedOverlays: Iterator<SemanticTokensBuilder>) {
        var currentTokenIndex = 0
        lastLine = 0
        lastCursor = 0
        for(overlay in sortedOverlays) {
            multilineTokenCount += overlay.multilineTokenCount
            var srcLine = 0
            var srcCursor = 0
            srcTokens@for(i in 0 until overlay.data.size step 5) {
                srcLine += overlay.data[i]
                srcCursor = overlay.data[i + 1] + if(overlay.data[i] == 0) srcCursor else 0
                val srcLength = overlay.data[i + 2]
                val srcTypeId = overlay.data[i + 3]
                val srcModifiers = overlay.data[i + 4]

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
                        val lineDiff: Int

                        if(newDestCursor < srcCursor) {
                            // Src token doesn't cover start of dest token, so trim dest token length
                            cursorDiff = srcCursor - newDestCursor
                            lineDiff = 0 // The two tokens must be on the same line
                            data[currentTokenIndex + 2] = cursorDiff
                            currentTokenIndex += 5
                        } else {
                            // Token is removed, becaise its start is covered by the src token. The remaining part of the dest token will be added back later
                            data.subList(currentTokenIndex, currentTokenIndex + 5).clear()
                            // Use previous destCursor because the token that starts at newDestCursor has been removed
                            // But if the previous token was on a separate line, the column value is just the srcCursor, because this token will advance to the next line
                            cursorDiff = srcCursor - (if(lastLine == srcLine) lastCursor else 0)
                            lineDiff = srcLine - lastLine
                        }

                        data.add(currentTokenIndex, lineDiff)
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
                        } else if(newDestCursor < srcCursor) {
                            // Set position to dest, because the dest token is going to be the previous token when reading the src token that has just been placed at currentTokenIndex
                            lastLine = newDestLine
                            lastCursor = newDestCursor
                        }

                        if(currentTokenIndex + 5 < data.size && data[currentTokenIndex + 5] == 0) {
                            // Adjust position of next token cursor, which will require a different offset now

                            // Always use the cursorDiff between the current src token and dest token instead of the previous dest token (which is normally used when src token covers the start of dest token)
                            // Because the next token position is also relative to the current dest token, not the previous one.
                            val originalCursorDiff = srcCursor - newDestCursor

                            if(hasRemainingDest) {
                                data[currentTokenIndex + 6] -= originalCursorDiff + srcLength
                            } else {
                                // Also remove tokens that are completely covered and adjust the length of the last token if it overlaps
                                var accumulatedCursorDiff = -originalCursorDiff
                                do {
                                    accumulatedCursorDiff += data[currentTokenIndex + 6]
                                    if(accumulatedCursorDiff >= srcLength) {
                                        // Next token starts after the src token
                                        data[currentTokenIndex + 6] = accumulatedCursorDiff
                                        break
                                    }
                                    // Next token overlaps
                                    val removedLength = srcLength - accumulatedCursorDiff
                                    val remainingTokenLength = data[currentTokenIndex + 7] - removedLength
                                    if(remainingTokenLength > 0) {
                                        data[currentTokenIndex + 6] = srcLength
                                        data[currentTokenIndex + 7] = remainingTokenLength
                                        if(currentTokenIndex + 10 < data.size && data[currentTokenIndex + 10] == 0)
                                            data[currentTokenIndex + 11] = data[currentTokenIndex + 11] - removedLength
                                        break
                                    }
                                    // Token is completely covered, can be removed
                                    data.subList(currentTokenIndex + 5, currentTokenIndex + 10).clear()
                                } while(currentTokenIndex + 5 < data.size && data[currentTokenIndex + 5] == 0)
                            }
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

    fun cutAfter(cutPosition: Position) {
        var tokenPosition = Position()
        for(i in 0 until data.size step 5) {
            val previousPos = tokenPosition
            tokenPosition = tokenPosition.offsetBy(Position(data[i], data[i + 1]))

            if(tokenPosition >= cutPosition) {
                data.subList(i, data.size).clear()
                lastLine = previousPos.line
                lastCursor = previousPos.character
                return
            }
            val tokenLength = data[i + 2]
            if(tokenPosition.advance(tokenLength) >= cutPosition) {
                data[i + 2] = cutPosition.character - tokenPosition.character
                data.subList(i + 5, data.size).clear()
                lastLine = tokenPosition.line
                lastCursor = tokenPosition.character
                return
            }
        }
    }

    fun offset(position: Position) {
        if(data.isEmpty()) return
        if(data[0] == 0)
            // First token is on first line, so it's affected by the first line being moved to the right
            data[1] += position.character
        data[0] += position.line

        if(lastLine == 0)
            lastCursor += position.character
        lastLine += position.line
    }

    fun undoOffset(position: Position) {
        if(data.isEmpty()) return
        lastLine -= position.line
        if(lastLine == 0)
            lastCursor -= position.character
        data[0] -= position.line
        if(data[0] == 0)
            // First token is on first line, so it's affected by the first line being moved to the right
            data[1] -= position.character
    }

    fun clear() {
        data.clear()
        lastLine = 0
        lastCursor = 0
    }

    fun isEmpty() = data.isEmpty()

    fun build() = SemanticTokens(data)

    /**
     * Removes all semantic tokens that lie within any of the provided sorted ranges.
     *
     * @param sortedRanges A sorted list of ranges to remove tokens from. Ranges must not overlap.
     */
    fun removeTokensInRanges(sortedRanges: List<Range>) {
        if(sortedRanges.isEmpty() || data.isEmpty())
            return

        var currentTokenIndex = 0
        var lastTokenPosition = Position(0, 0)
        var rangeIndex = 0

        while(currentTokenIndex < data.size && rangeIndex < sortedRanges.size) {
            val tokenLineDelta = data[currentTokenIndex]
            val tokenCharDelta = data[currentTokenIndex + 1]
            val tokenLength = data[currentTokenIndex + 2]
            val tokenTypeId = data[currentTokenIndex + 3]
            val tokenModifiers = data[currentTokenIndex + 4]

            val tokenStart = lastTokenPosition.offsetBy(Position(tokenLineDelta, tokenCharDelta))
            val tokenEnd = Position(tokenStart.line, tokenStart.character + tokenLength)

            val range = sortedRanges[rangeIndex]
            val rangeStart = range.start
            val rangeEnd = range.end

            if(tokenEnd <= rangeStart) {
                // Token ends before this range, move to next token
                currentTokenIndex += 5
                lastTokenPosition = tokenStart
                continue
            }

            if(tokenStart >= rangeEnd) {
                // Token starts after this range, try next range
                rangeIndex++
                continue
            }

            if(tokenStart >= rangeStart && tokenEnd <= rangeEnd) {
                // Token is completely within the range, remove it
                data.subList(currentTokenIndex, currentTokenIndex + 5).clear()
                // Adjust next token position (which is now at currentTokenIndex)
                if(currentTokenIndex < data.size) {
                    if(data[currentTokenIndex] == 0)
                        data[currentTokenIndex + 1] += tokenCharDelta
                    data[currentTokenIndex] += tokenLineDelta
                }
                continue
            }

            // Token partially overlaps with the range, need to split it
            if(tokenStart < rangeStart) {
                // token start and range start must be on same line. Keep the part before the range
                val keepLength = rangeStart.character - tokenStart.character
                data[currentTokenIndex + 2] = keepLength
                currentTokenIndex += 5
                lastTokenPosition = tokenStart

                if(rangeEnd < tokenEnd) {
                    // Add the remaining part after the range
                    val remainingLength = tokenEnd.character - rangeEnd.character
                    val skippedCharacters = rangeEnd.character - tokenStart.character
                    data.add(currentTokenIndex, 0)
                    data.add(currentTokenIndex + 1, skippedCharacters)
                    data.add(currentTokenIndex + 2, remainingLength)
                    data.add(currentTokenIndex + 3, tokenTypeId)
                    data.add(currentTokenIndex + 4, tokenModifiers)
                    // Adjust next token position
                    if(currentTokenIndex + 5 < data.size && data[currentTokenIndex + 5] == 0) {
                        data[currentTokenIndex + 6] -= skippedCharacters
                    }
                    rangeIndex++
                }
            } else {
                // Keep the part after the range
                val remainingLength = tokenEnd.character - rangeEnd.character
                val skippedCharacters = tokenLength - remainingLength
                data[currentTokenIndex + 1] += skippedCharacters
                data[currentTokenIndex + 2] = remainingLength
                // Adjust next token position
                if(currentTokenIndex + 5 < data.size && data[currentTokenIndex + 5] == 0) {
                    data[currentTokenIndex + 6] -= skippedCharacters
                }
                lastTokenPosition = tokenStart
                currentTokenIndex += 5
                rangeIndex++
            }
        }

        if(currentTokenIndex >= sortedRanges.size) {
            lastLine = lastTokenPosition.line
            lastCursor = lastTokenPosition.character
        }
    }

    object PrettyJacksonSerializer : JsonSerializer<SemanticTokensBuilder>() {
        override fun serialize(
            value: SemanticTokensBuilder,
            gen: JsonGenerator,
            serializers: SerializerProvider,
        ) {
            val prevPrettyPrinter = gen.prettyPrinter
            if(prevPrettyPrinter is DefaultPrettyPrinter) {
                gen.setPrettyPrinter(
                    DefaultPrettyPrinter(prevPrettyPrinter).apply {
                        indentArraysWith(object : DefaultPrettyPrinter.Indenter {
                            var index = 0

                            override fun writeIndentation(
                                g: JsonGenerator,
                                level: Int,
                            ) {
                                val i = index++
                                if(i == 0 || i == value.data.size) {
                                    // Newline at start and end
                                    DefaultIndenter.SYSTEM_LINEFEED_INSTANCE.writeIndentation(g, level)
                                    return
                                }
                                if(i % 5 != 0) {
                                    // No whitespace inside a token
                                    return
                                }
                                if(value.data[i] == 0) {
                                    // Separate tokens
                                    DefaultPrettyPrinter.FixedSpaceIndenter.instance.writeIndentation(g, level)
                                    return
                                }
                                for(i in 0 until value.data[i]) {
                                    // Newline for every line the token advanced
                                    DefaultIndenter.SYSTEM_LINEFEED_INSTANCE.writeIndentation(g, level)
                                }
                            }

                            override fun isInline() = false
                        })
                    }
                )
            }
            gen.writeStartArray()
            for(i in 0 until value.data.size) {
                gen.writeNumber(value.data[i])
            }
            gen.writeEndArray()
            gen.setPrettyPrinter(prevPrettyPrinter)
        }
    }

    /**
     * Helper class to shift semantic tokens in the middle of the builder
     */
    inner class TokenPositionMapper {
        private var currentTokenIndex = 0
        private var prevTokenPosition = Position()
        private var currentTokenPosition = if(data.isEmpty()) Position(Int.MAX_VALUE, Int.MAX_VALUE) else Position(data[0], data[1])

        /**
         * Shifts all semantic tokens after the source position by the difference between
         * source and target position. Multiple calls should only have increasing sourcePosition values
         *
         * If `addMapping` has been called before, then the `sourcePosition` of all following calls should already
         * have that mapping applied to it. This means `sourcePosition` always references the current state of the semantic tokens,
         * not the state when the `TokenPositionMapper` was created.
         *
         * If the [sourcePosition] is within a token, the token's length will *not* be adjusted. This method only shifts the start of tokens.
         *
         * @param sourcePosition The position after which tokens should be shifted
         * @param targetPosition The new position of sourcePosition
         */
        fun addMapping(sourcePosition: Position, targetPosition: Position) {
            // Advance from the cached position to find where we need to start shifting
            while (currentTokenPosition <= sourcePosition) {
                prevTokenPosition.line = currentTokenPosition.line
                prevTokenPosition.character = currentTokenPosition.character

                currentTokenIndex += 5

                if(currentTokenIndex >= data.size)
                    break

                val lineDelta = data[currentTokenIndex]
                val charDelta = data[currentTokenIndex + 1]

                currentTokenPosition.line += lineDelta
                if(lineDelta == 0) {
                    currentTokenPosition.character += charDelta
                } else {
                    currentTokenPosition.character = charDelta
                }
            }

            // Now shift all remaining tokens. The only token that must be adjusted
            // is the one at currentTokenIndex; all following tokens are relative to it.
            if(currentTokenIndex >= data.size)
                return
            currentTokenPosition = targetPosition.offsetBy(sourcePosition.differenceTo(currentTokenPosition))
            val newTokenDelta = prevTokenPosition.differenceTo(currentTokenPosition)
            data[currentTokenIndex] = newTokenDelta.line
            data[currentTokenIndex + 1] = newTokenDelta.character
        }
    }
}