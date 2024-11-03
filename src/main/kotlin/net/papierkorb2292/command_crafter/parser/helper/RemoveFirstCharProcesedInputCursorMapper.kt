package net.papierkorb2292.command_crafter.parser.helper

object RemoveFirstCharProcessedInputCursorMapper : ProcessedInputCursorMapper {
    override fun mapToTarget(sourceCursor: Int, clampInGaps: Boolean) =
        if(sourceCursor > 0) sourceCursor - 1 else sourceCursor

    override fun mapToSource(targetCursor: Int, clampInGaps: Boolean) = targetCursor + 1
}