package net.papierkorb2292.command_crafter.parser.helper

import com.mojang.brigadier.context.StringRange
import net.papierkorb2292.command_crafter.editor.processing.helper.compareTo

class CombinedProcessedInputCursorMapper(entries: List<Entry>) : ProcessedInputCursorMapper {
    private val entries = entries.sortedBy { it.sourceRange.start }
    init {
        for(i in 1 until entries.size) {
            val previous = entries[i - 1]
            val current = entries[i]
            require(previous.sourceRange.end <= current.sourceRange.start && previous.targetRange.end <= current.targetRange.start) {
                "Entries must not overlap and target cursors must be in the same order as source cursors: $previous and $current"
            }
        }
    }

    override fun mapToTarget(sourceCursor: Int, clampInGaps: Boolean): Int {
        val entryIndex = entries.binarySearch { -sourceCursor.compareTo(it.sourceRange) }
        val entry = if(entryIndex < 0) {
            val insertionPoint = -entryIndex - 1
            if(insertionPoint == 0) return sourceCursor
            val entry = entries[insertionPoint - 1]
            if(clampInGaps) {
                return entry.mapper.mapToTarget(0, true) + entry.targetRange.start
            }
            entry
        } else {
            entries[entryIndex]
        }
        return entry.mapper.mapToTarget(sourceCursor - entry.sourceRange.start, clampInGaps) + entry.targetRange.start
    }

    override fun mapToTargetNoGaps(sourceCursor: Int): Int? {
        val entryIndex = entries.binarySearch { it.sourceRange.start.compareTo(sourceCursor) }
        if(entryIndex < 0) return null
        val entry = entries[entryIndex]
        val mappedCursor = entry.mapper.mapToTargetNoGaps(sourceCursor - entry.sourceRange.start) ?: return null
        return mappedCursor + entry.targetRange.start
    }

    override fun mapToSource(targetCursor: Int, clampInGaps: Boolean): Int {
        val entryIndex = entries.binarySearch { it.targetRange.start.compareTo(targetCursor) }
        val entry = if(entryIndex < 0) {
            val insertionPoint = -entryIndex - 1
            if(insertionPoint == 0) return targetCursor
            val entry = entries[insertionPoint - 1]
            if(clampInGaps) {
                return entry.mapper.mapToSource(0, true) + entry.sourceRange.start
            }
            entry
        } else {
            entries[entryIndex]
        }
        return entry.mapper.mapToSource(targetCursor - entry.targetRange.start, clampInGaps) + entry.sourceRange.start
    }

    override fun mapToSourceNoGaps(targetCursor: Int): Int? {
        val entryIndex = entries.binarySearch { it.targetRange.start.compareTo(targetCursor) }
        if(entryIndex < 0) return null
        val entry = entries[entryIndex]
        val mappedCursor = entry.mapper.mapToTargetNoGaps(targetCursor - entry.targetRange.start) ?: return null
        return mappedCursor + entry.sourceRange.start
    }

    class Entry(val sourceRange: StringRange, val targetRange: StringRange, val mapper: ProcessedInputCursorMapper)
}