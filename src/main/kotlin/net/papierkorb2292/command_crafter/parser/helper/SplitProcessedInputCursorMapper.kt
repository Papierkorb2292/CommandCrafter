package net.papierkorb2292.command_crafter.parser.helper

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
class SplitProcessedInputCursorMapper : ProcessedInputCursorMapper {
    val sourceCursors = IntList()
    val targetCursors = IntList()
    val lengths = IntList()

    var prevSourceEnd: Int = 0
    var prevTargetEnd: Int = 0

    fun addMapping(sourceCursor: Int, targetCursor: Int, length: Int) {
        require(sourceCursor >= prevSourceEnd && targetCursor >= prevTargetEnd) { "Mappings must be added in order" }
        sourceCursors.add(sourceCursor)
        targetCursors.add(targetCursor)
        lengths.add(length)
        prevSourceEnd = sourceCursor + length
        prevTargetEnd = targetCursor + length
    }

    fun addFollowingMapping(sourceCursor: Int, length: Int) {
        addMapping(sourceCursor, prevTargetEnd, length)
    }

    override fun mapToTarget(sourceCursor: Int, clampInGaps: Boolean): Int {
        return map(sourceCursors, targetCursors, sourceCursor, clampInGaps)
    }
    override fun mapToTargetNoGaps(sourceCursor: Int): Int? {
        return mapNoGaps(sourceCursors, targetCursors, sourceCursor)
    }

    override fun mapToSource(targetCursor: Int, clampInGaps: Boolean): Int {
        return map(targetCursors, sourceCursors, targetCursor, clampInGaps)
    }
    override fun mapToSourceNoGaps(targetCursor: Int): Int? {
        return mapNoGaps(targetCursors, sourceCursors, targetCursor)
    }

    private fun map(inputCursors: IntList, outputCursors: IntList, inputCursor: Int, clampInGaps: Boolean): Int {
        var mappingIndex = inputCursors.binarySearch { index ->
            if(inputCursors[index] > inputCursor) 1
            else if (inputCursors[index] + lengths[index] < inputCursor) -1
            else 0
        }
        if(mappingIndex < 0) {
            if(mappingIndex == -1) {
                return inputCursor
            }
            mappingIndex = -(mappingIndex + 2)
            if(clampInGaps) {
                return outputCursors[mappingIndex] + lengths[mappingIndex]
            }
        }
        val startInputCursor = inputCursors[mappingIndex]
        val relativeCursor = inputCursor - startInputCursor
        return outputCursors[mappingIndex] + relativeCursor
    }

    private fun mapNoGaps(inputCursors: IntList, outputCursors: IntList, inputCursor: Int): Int? {
        val mappingIndex = inputCursors.binarySearch { index ->
            if(inputCursors[index] > inputCursor) 1
            else if (inputCursors[index] + lengths[index] < inputCursor) -1
            else 0
        }
        if(mappingIndex < 0)
            return null
        val startInputCursor = inputCursors[mappingIndex]
        val relativeCursor = inputCursor - startInputCursor
        return outputCursors[mappingIndex] + relativeCursor
    }
}