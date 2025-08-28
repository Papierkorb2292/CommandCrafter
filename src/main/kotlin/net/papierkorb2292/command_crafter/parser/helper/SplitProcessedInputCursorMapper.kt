package net.papierkorb2292.command_crafter.parser.helper

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import net.papierkorb2292.command_crafter.helper.IntList
import net.papierkorb2292.command_crafter.helper.binarySearch
import kotlin.collections.component1
import kotlin.collections.component2
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

    /**
     * Maps source cursor position to the last cursor position that can be regarded as belonging to the same character in the target string.
     *
     * This can be used to represent escaped characters in the source string that are resolved in the target string.
     */
    val expandedCharEnds = Int2IntOpenHashMap()

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

    fun addExpandedChar(startCursor: Int, endCursor: Int) {
        this.expandedCharEnds[startCursor] = endCursor
    }

    fun removeNegativeTargetCursors() {
        while(!lengths.isEmpty()) {
            val targetCursor = targetCursors[0]
            val length = lengths[0]
            if(lengths.size == 1 || targetCursors[1] >= 0 || targetCursor + length >= 0) {
                if(targetCursor >= 0)
                    break
                val newLength = max(length + targetCursor, 0)
                lengths[0] = newLength
                sourceCursors[0] -= targetCursor
                targetCursors[0] = 0
                break
            }
            sourceCursors.remove(0)
            targetCursors.remove(0)
            lengths.remove(0)
        }
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

    fun containsSourceCursor(sourceCursor: Int, endInclusive: Boolean = false) = containsCursor(sourceCursor, sourceCursors, endInclusive)

    fun containsTargetCursor(targetCursor: Int, endInclusive: Boolean = false) = containsCursor(targetCursor, targetCursors, endInclusive)

    private fun containsCursor(inputCursor: Int, inputCursors: IntList, endInclusive: Boolean): Boolean {
        val endInclusiveOffset = if(endInclusive) 1 else 0
        return 0 <= inputCursors.binarySearch { index ->
            if(inputCursors[index] > inputCursor) 1
            else if (inputCursors[index] + lengths[index] + endInclusiveOffset <= inputCursor) -1
            else 0
        }
    }

    fun combineWith(targetMapper: OffsetProcessedInputCursorMapper): SplitProcessedInputCursorMapper {
        val result = SplitProcessedInputCursorMapper()
        for(i in 0 until sourceCursors.size) {
            result.addMapping(sourceCursors[i], targetCursors[i] + targetMapper.offset, lengths[i])
        }
        for((startCursor, endCursor) in expandedCharEnds)
            result.addExpandedChar(startCursor + targetMapper.offset, endCursor + targetMapper.offset)
        return result
    }

    fun combineWith(originalTargetMapper: SplitProcessedInputCursorMapper): SplitProcessedInputCursorMapper {
        val result = SplitProcessedInputCursorMapper()
        // Make mappers mutable so any processed mappings can be removed, which makes the computation easier
        val targetMapper = originalTargetMapper.copy()
        val sourceMapper = this.copy()

        while(!targetMapper.lengths.isEmpty() && !sourceMapper.lengths.isEmpty()) {
            val start = max(sourceMapper.targetCursors[0], targetMapper.sourceCursors[0])
            val end = min(sourceMapper.targetCursors[0] + sourceMapper.lengths[0], targetMapper.sourceCursors[0] + targetMapper.lengths[0])
            val length = end - start
            result.addMapping(
                start - sourceMapper.targetCursors[0] + sourceMapper.sourceCursors[0],
                start - targetMapper.sourceCursors[0] + targetMapper.targetCursors[0],
                length
            )

            // Mappings should only be kept, if they also intersect the next mapping in the other mapper
            val canDiscardSourceMapping = targetMapper.sourceCursors.size <= 1 || sourceMapper.targetCursors[0] + sourceMapper.lengths[0] < targetMapper.sourceCursors[1]
            val canDiscardTargetMapping = sourceMapper.targetCursors.size <= 1 || targetMapper.sourceCursors[0] + targetMapper.lengths[0] < sourceMapper.targetCursors[1]
            if(canDiscardSourceMapping)
                sourceMapper.popFirstMapping()
            if(canDiscardTargetMapping)
                targetMapper.popFirstMapping()
        }

        val expandedCharCursors = targetMapper.expandedCharEnds.keys.iterator()
        while(expandedCharCursors.hasNext()) {
            val startCursor = expandedCharCursors.nextInt()
            val endCursor = targetMapper.expandedCharEnds[startCursor]
            val mappedStart = mapToSource(startCursor, false)
            val mappedEnd = mapToSource(endCursor, false)
            result.addExpandedChar(mappedStart, mappedEnd)
        }
        return result
    }

    fun copy(): SplitProcessedInputCursorMapper {
        val result = SplitProcessedInputCursorMapper()
        result.copyFrom(this)
        return result
    }

    fun copyFrom(other: SplitProcessedInputCursorMapper) {
        sourceCursors.clear()
        targetCursors.clear()
        lengths.clear()
        expandedCharEnds.clear()
        sourceCursors.addAll(other.sourceCursors)
        targetCursors.addAll(other.targetCursors)
        lengths.addAll(other.lengths)
        expandedCharEnds.putAll(other.expandedCharEnds)
        prevSourceEnd = other.prevSourceEnd
        prevTargetEnd = other.prevTargetEnd
    }

    private fun popFirstMapping() {
        sourceCursors.remove(0)
        targetCursors.remove(0)
        lengths.remove(0)
    }
}