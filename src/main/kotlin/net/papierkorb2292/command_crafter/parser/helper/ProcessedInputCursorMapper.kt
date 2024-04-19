package net.papierkorb2292.command_crafter.parser.helper

import com.mojang.brigadier.context.StringRange

interface ProcessedInputCursorMapper {
    fun mapToTarget(sourceCursor: Int, clampInGaps: Boolean = false): Int
    fun mapToTarget(sourceRange: StringRange, clampInGaps: Boolean = false): StringRange {
        return StringRange(mapToTarget(sourceRange.start, clampInGaps), mapToTarget(sourceRange.end, clampInGaps))
    }

    fun mapToTargetNoGaps(sourceCursor: Int): Int?
    fun mapToTargetNoGaps(sourceRange: StringRange): StringRange? {
        val start = mapToTargetNoGaps(sourceRange.start) ?: return null
        val end = mapToTargetNoGaps(sourceRange.end) ?: return null
        return StringRange(start, end)
    }

    fun mapToSource(targetCursor: Int, clampInGaps: Boolean = false): Int
    fun mapToSource(targetRange: StringRange, clampInGaps: Boolean = false): StringRange {
        return StringRange(mapToSource(targetRange.start, clampInGaps), mapToSource(targetRange.end, clampInGaps))
    }

    fun mapToSourceNoGaps(targetCursor: Int): Int?
    fun mapToSourceNoGaps(targetRange: StringRange): StringRange? {
        val start = mapToSourceNoGaps(targetRange.start) ?: return null
        val end = mapToSourceNoGaps(targetRange.end) ?: return null
        return StringRange(start, end)
    }
}

fun ProcessedInputCursorMapper.thenMapWith(other: ProcessedInputCursorMapper): ProcessedInputCursorMapper {
    val first = this
    return object : ProcessedInputCursorMapper {
        override fun mapToTarget(sourceCursor: Int, clampInGaps: Boolean) = other.mapToTarget(first.mapToTarget(sourceCursor, clampInGaps), clampInGaps)
        override fun mapToTargetNoGaps(sourceCursor: Int): Int? {
            return other.mapToTargetNoGaps(first.mapToTargetNoGaps(sourceCursor) ?: return null)
        }
        override fun mapToSource(targetCursor: Int, clampInGaps: Boolean) = first.mapToSource(other.mapToSource(targetCursor, clampInGaps), clampInGaps)
        override fun mapToSourceNoGaps(targetCursor: Int): Int? {
            return first.mapToSourceNoGaps(other.mapToSourceNoGaps(targetCursor) ?: return null)
        }
    }
}