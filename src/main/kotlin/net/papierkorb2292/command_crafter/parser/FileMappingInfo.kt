package net.papierkorb2292.command_crafter.parser

import net.papierkorb2292.command_crafter.editor.processing.helper.advance
import net.papierkorb2292.command_crafter.editor.processing.helper.advanceLine
import net.papierkorb2292.command_crafter.parser.helper.SplitProcessedInputCursorMapper
import org.apache.commons.compress.harmony.pack200.IntList
import org.eclipse.lsp4j.Position
import java.io.IOException
import java.io.Reader
import java.util.*

class FileMappingInfo(
    val lines: List<String>,
    val cursorMapper: SplitProcessedInputCursorMapper = SplitProcessedInputCursorMapper(),
    var readCharacters: Int = 0,
    var skippedChars: Int = 0,
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

    fun copy() = FileMappingInfo(lines, cursorMapper, readCharacters, skippedChars)


    //TODO: Use cursorMapper
    fun getReader(startCursor: Int) = object : Reader() {
        private var isClosed = false
        private var pos = Position()

        init {
            for(i in 0 until startCursor)
                readChar()
        }

        fun canRead() = pos.line < lines.size && (pos.line + 1 < lines.size || pos.character < lines.last().length)

        fun readChar(): Char {
            val line = lines[pos.line]
            return if(pos.character >= line.length) {
                pos = pos.advanceLine()
                '\n'
            } else {
                val char = line[pos.character]
                pos = pos.advance()
                char
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