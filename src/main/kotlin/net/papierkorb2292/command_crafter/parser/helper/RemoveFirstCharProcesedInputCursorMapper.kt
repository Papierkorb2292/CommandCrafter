package net.papierkorb2292.command_crafter.parser.helper

object RemoveFirstCharProcessedInputCursorMapper : ProcessedInputCursorMapper {
    override fun mapToTarget(sourceCursor: Int, clampInGaps: Boolean) =
        if(sourceCursor > 0) sourceCursor - 1 else sourceCursor

    override fun mapToTargetNoGaps(sourceCursor: Int) =
        if(sourceCursor > 0) sourceCursor - 1 else null

    override fun mapToSource(targetCursor: Int, clampInGaps: Boolean) = targetCursor + 1

    override fun mapToSourceNoGaps(targetCursor: Int) = targetCursor + 1
}