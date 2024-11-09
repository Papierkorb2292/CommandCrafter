package net.papierkorb2292.command_crafter.parser.helper

import net.papierkorb2292.command_crafter.helper.IntList
import net.papierkorb2292.command_crafter.helper.binarySearch
import kotlin.math.max
import kotlin.math.min

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
        require(sourceCursors.isEmpty() || sourceCursor >= prevSourceEnd && targetCursor >= prevTargetEnd) { "Mappings must be added in order" }
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

    override fun mapToSource(targetCursor: Int, clampInGaps: Boolean): Int {
        return map(targetCursors, sourceCursors, targetCursor, clampInGaps)
    }

    private fun map(inputCursors: IntList, outputCursors: IntList, inputCursor: Int, clampInGaps: Boolean): Int {
        var mappingIndex = inputCursors.binarySearch { index ->
            if(inputCursors[index] > inputCursor) 1
            else if (inputCursors[index] + lengths[index] <= inputCursor) -1
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

    fun combineWith(targetMapper: OffsetProcessedInputCursorMapper): SplitProcessedInputCursorMapper {
        val result = SplitProcessedInputCursorMapper()
        for(i in 0 until sourceCursors.size) {
            result.addMapping(sourceCursors[i], targetCursors[i] + targetMapper.offset, lengths[i])
        }
        return result
    }

    fun combineWith(targetMapper: SplitProcessedInputCursorMapper): SplitProcessedInputCursorMapper {
        val result = SplitProcessedInputCursorMapper()
        val otherIndices = (0 until targetMapper.sourceCursors.size).iterator()
        var currentOtherIndex = if(otherIndices.hasNext()) otherIndices.nextInt() else -1
        for(i in 0 until sourceCursors.size) {
            val sourceCursor = sourceCursors[i]
            val targetCursor = targetCursors[i]
            // Skip targetMapper mappings that are before the range of mapping i
            while(currentOtherIndex != -1 && targetMapper.sourceCursors[currentOtherIndex] + targetMapper.lengths[currentOtherIndex] < targetCursor) {
                currentOtherIndex = if(otherIndices.hasNext()) otherIndices.nextInt() else -1
            }
            if(currentOtherIndex == -1 || targetMapper.sourceCursors[currentOtherIndex] > targetCursor) {
                // targetMapper has no mapping at the start of this range
                // Thus, a mapping of length=0 is added at the start,
                // such that all cursors up until the next mapping are part of a gap but are still translated correctly
                if(targetMapper.sourceCursors.isEmpty() || currentOtherIndex == 0) {
                    result.addMapping(sourceCursor, targetCursor, 0)
                } else {
                    val prevOtherIndex = if(currentOtherIndex == -1) targetMapper.sourceCursors.size - 1 else currentOtherIndex - 1
                    val translation = targetMapper.targetCursors[prevOtherIndex] - targetMapper.sourceCursors[prevOtherIndex]
                    result.addMapping(sourceCursor, targetCursor + translation, 0)
                }
                if(currentOtherIndex == -1)
                    continue
            }
            var nextMappingMinSourceStart = sourceCursor
            // Go through targetMapper mappings that intersect with mapping i
            while(currentOtherIndex != -1 && targetMapper.sourceCursors[currentOtherIndex] < targetCursor + lengths[i]) {
                val mappedOtherStart = targetMapper.sourceCursors[currentOtherIndex] - targetCursor + sourceCursor
                val sourceStart = max(mappedOtherStart, sourceCursor)
                val mappingLength = min(min(mappedOtherStart - sourceCursor, 0) + targetMapper.lengths[currentOtherIndex], sourceCursor + lengths[i] - nextMappingMinSourceStart)
                result.addMapping(sourceStart, targetMapper.targetCursors[currentOtherIndex] - mappedOtherStart + sourceStart, mappingLength)
                nextMappingMinSourceStart = sourceStart + mappingLength
                currentOtherIndex = if(otherIndices.hasNext()) otherIndices.nextInt() else -1
            }
        }
        return result
    }
}