package net.papierkorb2292.command_crafter.parser

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.advance
import net.papierkorb2292.command_crafter.editor.processing.helper.advanceLine
import net.papierkorb2292.command_crafter.helper.IntList
import net.papierkorb2292.command_crafter.helper.binarySearch
import net.papierkorb2292.command_crafter.parser.helper.SplitProcessedInputCursorMapper
import org.eclipse.lsp4j.Position
import java.io.IOException
import java.io.Reader
import java.util.*

class FileMappingInfo(
    val lines: List<String>,
    val cursorMapper: SplitProcessedInputCursorMapper = SplitProcessedInputCursorMapper(),
    var readCharacters: Int = 0,
    var skippedChars: Int = 0,
    val positionFromCursorFIFOCache: Int2ObjectLinkedOpenHashMap<Position> = Int2ObjectLinkedOpenHashMap(8, 0.25F),
) {
    val accumulatedLineLengths = IntList(lines.size)
    init {
        var accumulatedLength = 0
        for(line in lines) {
            accumulatedLength += line.length + 1
            accumulatedLineLengths.add(accumulatedLength)
        }
    }

    val readSkippingChars
        get() = readCharacters - skippedChars

    fun copy() = FileMappingInfo(lines, cursorMapper, readCharacters, skippedChars, positionFromCursorFIFOCache)

    fun getReader(startCursor: Int) = object : Reader() {
        private var isClosed = false
        private var currentCursor = startCursor
        private var currentMappingIndex = cursorMapper.targetCursors.binarySearch { index ->
            if(cursorMapper.targetCursors[index] > startCursor) 1
            else if (cursorMapper.targetCursors[index] + cursorMapper.lengths[index] <= startCursor) -1
            else 0
        }
        init {
            if(currentMappingIndex < 0) {
                currentMappingIndex = -currentMappingIndex - 2
            }
        }
        private var pos = AnalyzingResult.getPositionFromCursor(startCursor - cursorMapper.targetCursors[currentMappingIndex] + cursorMapper.sourceCursors[currentMappingIndex], this@FileMappingInfo)

        private val endCursor = cursorMapper.mapToTarget(accumulatedLineLengths.last())

        fun canRead() = currentCursor < endCursor

        private fun readChar(): Char {
            if(!canRead()) throw IOException("End of stream reached")
            val line = lines[pos.line]
            val char =
                if(pos.character >= line.length) '\n'
                else line[pos.character]
            currentCursor++
            if(currentCursor == endCursor) {
                return char
            }
            if(currentMappingIndex + 1 >= cursorMapper.targetCursors.size) {
                skipSourceChars(1)
                return char
            }
            val nextMappingStart = cursorMapper.targetCursors[currentMappingIndex + 1]
            if(currentCursor == nextMappingStart) {
                val currentSourceCursor = if(currentMappingIndex >= 0) {
                    val relativeCursor = currentCursor - 1 - cursorMapper.targetCursors[currentMappingIndex]
                    cursorMapper.sourceCursors[currentMappingIndex] + relativeCursor
                } else currentCursor
                skipSourceChars(cursorMapper.sourceCursors[currentMappingIndex + 1] - currentSourceCursor)
                currentMappingIndex++
            } else {
                skipSourceChars(1)
            }
            return char
        }

        private fun skipSourceChars(sourceCharCount: Int) {
            var remainingChars = sourceCharCount
            while(true) {
                val line = lines[pos.line]
                val remainingLineChars = line.length - pos.character
                if(remainingLineChars >= remainingChars) {
                    pos = pos.advance(remainingChars)
                    return
                }
                pos = pos.advanceLine()
                remainingChars -= remainingLineChars + 1 // Account for skipped '\n'
            }
        }

        override fun read(cbuf: CharArray, off: Int, len: Int): Int {
            if(isClosed) throw IOException("Stream closed")
            Objects.checkFromIndexSize(off, len, cbuf.size)
            if(len == 0) return 0
            if(!canRead())
                return -1
            var bufIndex = 0
            while(canRead() && bufIndex < len) {
                cbuf[off + bufIndex++] = readChar()
            }
            return bufIndex
        }

        override fun close() {
            isClosed = true
        }
    }
}