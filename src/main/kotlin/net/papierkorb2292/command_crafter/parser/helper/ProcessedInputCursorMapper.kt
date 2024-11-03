package net.papierkorb2292.command_crafter.parser.helper

import com.mojang.brigadier.context.StringRange

interface ProcessedInputCursorMapper {
    fun mapToTarget(sourceCursor: Int, clampInGaps: Boolean = false): Int
    fun mapToTarget(sourceRange: StringRange, clampInGaps: Boolean = false): StringRange {
        return StringRange(mapToTarget(sourceRange.start, clampInGaps), mapToTarget(sourceRange.end, clampInGaps))
    }

    fun mapToSource(targetCursor: Int, clampInGaps: Boolean = false): Int
    fun mapToSource(targetRange: StringRange, clampInGaps: Boolean = false): StringRange {
        return StringRange(mapToSource(targetRange.start, clampInGaps), mapToSource(targetRange.end, clampInGaps))
    }
}

fun ProcessedInputCursorMapper.thenMapWith(other: ProcessedInputCursorMapper): ProcessedInputCursorMapper {
    val first = this
    return object : ProcessedInputCursorMapper {
        override fun mapToTarget(sourceCursor: Int, clampInGaps: Boolean) = other.mapToTarget(first.mapToTarget(sourceCursor, clampInGaps), clampInGaps)
        override fun mapToSource(targetCursor: Int, clampInGaps: Boolean) = first.mapToSource(other.mapToSource(targetCursor, clampInGaps), clampInGaps)
    }
}