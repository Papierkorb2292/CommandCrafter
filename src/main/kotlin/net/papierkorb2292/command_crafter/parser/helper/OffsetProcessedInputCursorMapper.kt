package net.papierkorb2292.command_crafter.parser.helper

class OffsetProcessedInputCursorMapper(val offset: Int): ProcessedInputCursorMapper {
    override fun mapToTarget(sourceCursor: Int, clampInGaps: Boolean) = sourceCursor + offset
    override fun mapToSource(targetCursor: Int, clampInGaps: Boolean) = targetCursor - offset
}