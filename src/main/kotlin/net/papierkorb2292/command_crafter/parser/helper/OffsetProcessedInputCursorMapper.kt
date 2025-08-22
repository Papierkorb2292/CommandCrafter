package net.papierkorb2292.command_crafter.parser.helper

class OffsetProcessedInputCursorMapper(val offset: Int): ProcessedInputCursorMapper {
    override fun mapToTarget(sourceCursor: Int, clampInGaps: Boolean) = sourceCursor + offset
    override fun mapToSource(targetCursor: Int, clampInGaps: Boolean) = targetCursor - offset

    fun combineWith(targetMapper: SplitProcessedInputCursorMapper): SplitProcessedInputCursorMapper {
        val result = SplitProcessedInputCursorMapper()
        for(i in 0 until targetMapper.sourceCursors.size) {
            result.addMapping(targetMapper.sourceCursors[i] + offset, targetMapper.targetCursors[i], targetMapper.lengths[i])
        }
        for((startCursor, endCursor) in targetMapper.expandedCharEnds)
            result.addExpandedChar(startCursor + offset, endCursor + offset)
        return result
    }
}