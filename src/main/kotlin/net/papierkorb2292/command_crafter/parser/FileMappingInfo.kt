package net.papierkorb2292.command_crafter.parser

import net.papierkorb2292.command_crafter.parser.helper.SplitProcessedInputCursorMapper
import org.apache.commons.compress.harmony.pack200.IntList

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
}