package net.papierkorb2292.command_crafter.parser.helper

import com.mojang.brigadier.context.StringRange
import net.papierkorb2292.command_crafter.helper.binarySearch
import org.apache.commons.compress.harmony.pack200.IntList

/**
 * This class is used to map between cursor positions,
 * when parts of an input string are replaced with a different string
 * of varying length.
 *
 * For example, when a vanilla multiline
 * command is concatenated into a single line, this class can
 * map between the cursors on the original reader and the cursors
 * on the single line reader.
 */
class ProcessedInputCursorMapper {
    val sourceCursors = IntList()
    val targetCursors = IntList()
    val lengths = IntList()

    var prevSourceEnd: Int = 0
    var prevTargetEnd: Int = 0

    fun addMapping(sourceCursor: Int, targetCursor: Int, length: Int) {
        if(sourceCursor < prevSourceEnd || targetCursor < prevTargetEnd) {
            throw IllegalArgumentException("Mappings must be added in order")
        }
        sourceCursors.add(sourceCursor)
        targetCursors.add(targetCursor)
        lengths.add(length)
        prevSourceEnd = sourceCursor + length
        prevTargetEnd = targetCursor + length
    }

    fun addFollowingMapping(sourceCursor: Int, length: Int) {
        addMapping(sourceCursor, prevTargetEnd, length)
    }

    fun mapToTarget(sourceCursor: Int): Int {
        return map(sourceCursors, targetCursors, sourceCursor)
    }
    fun mapToTarget(sourceRange: StringRange): StringRange {
        return StringRange(mapToTarget(sourceRange.start), mapToTarget(sourceRange.end))
    }
    fun mapToTargetNoGaps(sourceCursor: Int): Int? {
        return mapNoGaps(sourceCursors, targetCursors, sourceCursor)
    }
    fun mapToTargetNoGaps(sourceRange: StringRange): StringRange? {
        val start = mapToTargetNoGaps(sourceRange.start) ?: return null
        val end = mapToTargetNoGaps(sourceRange.end) ?: return null
        return StringRange(start, end)
    }
    fun mapToSource(targetCursor: Int): Int {
        return map(targetCursors, sourceCursors, targetCursor)
    }
    fun mapToSource(targetRange: StringRange): StringRange {
        return StringRange(mapToSource(targetRange.start), mapToSource(targetRange.end))
    }
    fun mapToSourceNoGaps(sourceCursor: Int): Int? {
        return mapNoGaps(targetCursors, sourceCursors, sourceCursor)
    }
    fun mapToSourceNoGaps(targetRange: StringRange): StringRange? {
        val start = mapToSourceNoGaps(targetRange.start) ?: return null
        val end = mapToSourceNoGaps(targetRange.end) ?: return null
        return StringRange(start, end)
    }

    fun getSourceGaps() = getGaps(sourceCursors)
    fun getTargetGaps() = getGaps(targetCursors)

    private fun getGaps(inputCursors: IntList): List<StringRange> {
        val gaps = mutableListOf<StringRange>()
        for(i in 0 until inputCursors.size() - 1) {
            val start = inputCursors[i]
            val end = inputCursors[i + 1]
            gaps.add(StringRange(start, end))
        }
        return gaps
    }

    private fun map(inputCursors: IntList, outputCursors: IntList, inputCursor: Int): Int {
        var mappingIndex = inputCursors.binarySearch { index ->
            if(inputCursors[index] <= inputCursor) -1
            else if (inputCursors[index] + lengths[index] > inputCursor) 1
            else 0
        }
        if(mappingIndex < 0) {
            if(mappingIndex == -1) {
                return inputCursor
            }
            mappingIndex = -(mappingIndex + 2)
        }
        val startInputCursor = inputCursors[mappingIndex]
        val relativeCursor = inputCursor - startInputCursor
        return outputCursors[mappingIndex] + relativeCursor
    }

    private fun mapNoGaps(inputCursors: IntList, outputCursors: IntList, inputCursor: Int): Int? {
        val mappingIndex = inputCursors.binarySearch { index ->
            if(inputCursors[index] <= inputCursor) -1
            else if (inputCursors[index] + lengths[index] > inputCursor) 1
            else 0
        }
        if(mappingIndex < 0)
            return null
        val startInputCursor = inputCursors[mappingIndex]
        val relativeCursor = inputCursor - startInputCursor
        return outputCursors[mappingIndex] + relativeCursor
    }
}