package net.papierkorb2292.command_crafter.editor.processing

import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.differenceTo
import net.papierkorb2292.command_crafter.helper.binarySearch
import net.papierkorb2292.command_crafter.helper.roundDownBinarySearch
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage
import kotlin.math.min

object MacroMerger {
    /**
     * Recursively overlays all analyzing results of the child macros onto the analyzing result of the parent.
     * This takes into account file modifications made to the macros and maps all analyzing results accordingly.
     */
    fun overlayMacros(analyzingResult: AnalyzingResult, newFile: FileMappingInfo, macros: AnalyzingResourceCreator.MacroCache): AnalyzingResult {
        val resultMapper = analyzingResult.createMapper(newFile)
        val childResults = ArrayList<AnalyzingResult>(macros.orderedMacros.size)
        for((i, child) in macros.orderedMacros.withIndex()) {
            val mappedOffset = newFile.cursorMapper.mapToSource(child.rangeInParent.start)
            val positionOffset = AnalyzingResult.getPositionFromCursor(mappedOffset, newFile)
            childResults += overlayMacros(child.analyzingResult, child.updatedFile, child.children).addOffset(analyzingResult, positionOffset, mappedOffset)
            val relativeOffset = macros.childModificationOffsets.get(i)
            if(relativeOffset != null && relativeOffset.isNonZero()) {
                resultMapper.addMapping(child.rangeInParent.start, child.rangeInParent.end, positionOffset, relativeOffset.cursorOffset, relativeOffset.fileOffset)
            }
        }
        return resultMapper.build().overlayAllCompressedSorted(childResults)
    }

    /**
     * Checks whether the changes between the previous and new file all are within one macro.
     * In that case the macro is reparsed and its range and accumulated modifications are also adjusted accordingly,
     * so the cached macros can be directly used for the new file.
     *
     * @param prevFile The old content of the entire source file, for which macros have been cached
     * @param newReader The reader for the new content of the source file
     * @return true if only one macro was modified, which has now been analyzed again, false otherwise
     */
    fun trackOutermostMacroModification(prevFile: FileMappingInfo, newReader: DirectiveStringReader<AnalyzingResourceCreator>): Boolean {
        val oldMacros = newReader.resourceCreator.previousCache?.macroCache ?: return false
        val newMacros = newReader.resourceCreator.newCache.macroCache
        if(oldMacros.orderedMacros.isEmpty()) return false

        val newFile = newReader.fileMappingInfo
        val prevLines = prevFile.lines
        val newLines = newFile.lines

        // Find the first line that's different
        val minLineCount = min(prevLines.size, newLines.size)
        var absoluteStartPosition = 0
        var startLine = 0
        while(startLine < minLineCount) {
            val prevLine = prevLines[startLine]
            val newLine = newLines[startLine]
            val commonPrefixLen = getCommonPrefixLength(prevLine, newLine)
            if(commonPrefixLen != prevLine.length || commonPrefixLen != newLine.length) {
                absoluteStartPosition += commonPrefixLen
                break
            }
            absoluteStartPosition += commonPrefixLen + 1 // Plus newline
            startLine++
        }

        // Find the macro that encompasses the first change. If the change is exactly at the start of the macro, there was no modification inside the macro so return false
        val targetCursor = prevFile.cursorMapper.mapToTarget(absoluteStartPosition)
        var macroIndex = oldMacros.orderedMacroStartInParent.binarySearch { oldMacros.orderedMacroStartInParent[it].compareTo(targetCursor) }
        if(macroIndex >= -1)
            return false // Exactly matched the start of a macro (>= 0) or no macro starts before this position (== -1)
        macroIndex = roundDownBinarySearch(macroIndex)
        val oldMacroNode = oldMacros.orderedMacros[macroIndex]
        val oldMacroAbsoluteEnd = prevFile.cursorMapper.mapToSource(oldMacroNode.rangeInParent.end)
        if(oldMacroAbsoluteEnd < absoluteStartPosition)
            return false // The position is after the end of the macro

        // Now find the last line that's different
        var absoluteEndDist = 0
        var matchedEndLines = 0
        while(matchedEndLines < minLineCount) {
            val prevLine = prevLines[prevLines.size - 1 - matchedEndLines]
            val newLine = newLines[newLines.size - 1 - matchedEndLines]
            val commonSuffixLen = getCommonSuffixLength(prevLine, newLine)
            if(commonSuffixLen != prevLine.length || commonSuffixLen != newLine.length) {
                absoluteEndDist += commonSuffixLen
                break
            }
            absoluteEndDist += commonSuffixLen + 1 // Plus newline
            matchedEndLines++
        }

        val oldMacroEndDist = prevFile.accumulatedLineLengths.last() - oldMacroAbsoluteEnd
        if(oldMacroEndDist > absoluteEndDist)
            return false // There's a change after the end of the macro

        // Parse the macro template
        val templateReader = DirectiveStringReader.createReaderAtAbsoluteCursor(
            newReader.fileMappingInfo,
            newReader.dispatcher,
            newReader.resourceCreator,
            prevFile.cursorMapper.mapToSource(oldMacroNode.rangeInParent.start)
        )
        val newDecodedMacro = oldMacroNode.input.parser.parse(templateReader) ?: return false
        val newMacroEndDist = templateReader.fileMappingInfo.accumulatedLineLengths.last() - newDecodedMacro.absoluteRange.end
        if(newMacroEndDist != oldMacroEndDist)
            return false // Something changed the range of the macro relative to the rest of the file. Can't use the cache.

        // Before analyzing the new macro, add all the macros before it to the new cache
        for(i in 0 until macroIndex)
            newMacros.addMacro(oldMacros.orderedMacros[i])

        // Analyze new macro
        val relevantLines = AnalyzingResult.getLinesBetweenCursors(newDecodedMacro.absoluteRange.start, newDecodedMacro.absoluteRange.end, templateReader.fileMappingInfo)
        val newInput = oldMacroNode.input.copy(lines = relevantLines)
        VanillaLanguage.analyzeMacroString(newInput, newDecodedMacro, oldMacroNode.children, newReader, newReader.resourceCreator.source)
        val newCursorOffset = newReader.fileMappingInfo.accumulatedLineLengths.last() - prevFile.accumulatedLineLengths.last()
        val newFileOffset = AnalyzingResult.getPositionFromCursor(oldMacroAbsoluteEnd, prevFile)
            .differenceTo(AnalyzingResult.getPositionFromCursor(newDecodedMacro.absoluteRange.end, newReader.fileMappingInfo))
        val newOffset = AnalyzingResourceCreator.MacroOffset(newCursorOffset, newFileOffset)
        newMacros.childModificationOffsets.put(macroIndex, oldMacros.childModificationOffsets.get(macroIndex)?.addAfter(newOffset) ?: newOffset)

        // Add all remaining cached macros with the new offset
        for(i in macroIndex + 1 until oldMacros.orderedMacros.size)
            newMacros.addMacro(oldMacros.orderedMacros[i].withOffset(newCursorOffset))

        return true
    }

    private fun getCommonPrefixLength(str1: String, str2: String): Int {
        @Suppress("StringReferentialEquality")
        if(str1 === str2) // Fast path, because OpenFile uses the same string instance if a line wasn't edited
            return str1.length
        val minLength = min(str1.length, str2.length)
        for(i in 0 until minLength) {
            if(str1[i] != str2[i])
                return i
        }
        return minLength
    }

    private fun getCommonSuffixLength(str1: String, str2: String): Int {
        @Suppress("StringReferentialEquality")
        if(str1 === str2) // Fast path, because OpenFile uses the same string instance if a line wasn't edited
            return str1.length
        val minLength = min(str1.length, str2.length)
        for(i in 1..minLength) {
            if(str1[str1.length - i] != str2[str2.length - i])
                return i - 1
        }
        return minLength
    }
}