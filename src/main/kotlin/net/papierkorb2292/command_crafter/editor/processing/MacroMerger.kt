package net.papierkorb2292.command_crafter.editor.processing

import net.papierkorb2292.command_crafter.editor.debugger.helper.plus
import net.papierkorb2292.command_crafter.editor.processing.MacroMerger.getModifiedAbsoluteStart
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.differenceTo
import net.papierkorb2292.command_crafter.helper.binarySearch
import net.papierkorb2292.command_crafter.helper.roundDownBinarySearch
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage
import org.eclipse.lsp4j.Range
import kotlin.math.min

object MacroMerger {
    /**
     * Recursively overlays all analyzing results of the child macros onto the analyzing result of the parent.
     * This takes into account file modifications made to the macros and maps all analyzing results accordingly.
     */
    fun overlayMacros(analyzingResult: AnalyzingResult, newFile: FileMappingInfo, macros: AnalyzingResourceCreator.MacroCache): AnalyzingResult {
        val filteredResult = analyzingResult.copy()

        // Remove semantic tokens behind all macros that have their own background tokens
        var tokenCursorOffset = 0
        val overridenTokenRanges = mutableListOf<Range>()
        for((i, child) in macros.orderedMacros.withIndex()) {
            val startCursor = child.fileRangeInParent.start - tokenCursorOffset
            val relativeOffset = macros.childModificationOffsets.get(i)
            if(relativeOffset != null)
                tokenCursorOffset += relativeOffset.cursorOffset
            val endCursor = child.fileRangeInParent.end - tokenCursorOffset

            if(macros.childBackgroundSemanticTokens.containsKey(i)) {
                overridenTokenRanges += Range(
                    AnalyzingResult.getPositionFromCursor(startCursor, filteredResult.mappingInfo),
                    AnalyzingResult.getPositionFromCursor(endCursor, filteredResult.mappingInfo)
                )
            }
        }
        filteredResult.semanticTokens.removeTokensInRanges(overridenTokenRanges)

        // Overlay macros
        val resultMapper = filteredResult.createMapper(newFile)
        val childResults = ArrayList<AnalyzingResult>(macros.orderedMacros.size)
        val backgroundTokensList = mutableListOf<SemanticTokensBuilder>()
        for((i, child) in macros.orderedMacros.withIndex()) {
            val absoluteOffset = child.fileRangeInParent.start
            val positionOffset = AnalyzingResult.getPositionFromCursor(absoluteOffset, newFile)
            val overlayedResult = overlayMacros(child.analyzingResult, child.updatedFile, child.children)
            if(child.variablesSemanticTokensOverlay != null)
                overlayedResult.semanticTokens.overlay(listOf(child.variablesSemanticTokensOverlay).iterator())
            childResults += overlayedResult
                .withStringEscaperActual(child.stringEscaper)
                .withStringEscaperPotential(child.stringEscaper)
                .addOffset(analyzingResult, positionOffset, absoluteOffset)
            val relativeOffset = macros.childModificationOffsets.get(i)
            if(relativeOffset != null && relativeOffset.isNonZero()) {
                resultMapper.addMapping(child.fileRangeInParent.start, positionOffset, child.fileRangeInParent.end, relativeOffset.cursorOffset, relativeOffset.fileOffset)
            }
            val backgroundTokens = macros.childBackgroundSemanticTokens.get(i)
            if(backgroundTokens != null)
                backgroundTokensList += backgroundTokens
        }
        val mappedResult = resultMapper.build()
        mappedResult.semanticTokens.overlay(backgroundTokensList.iterator())
        return mappedResult.overlayAllCompressedSorted(childResults)
    }

    /**
     * Checks whether the changes between the previous and new file all are within one macro.
     * In that case the macro is reparsed and its range and accumulated modifications are also adjusted accordingly,
     * so the cached macros can be directly used for the new file.
     *
     * @param prevFile The old content of the entire source file, for which macros have been cached
     * @param newReader The reader for the new content of the source file
     * @param modifiedRange The range that was modified between [prevFile] and [newReader]. If not present, it will be calculated from the two inputs
     * @param isNested Whether the macros are inside another macro. If `true`, [newReader] is expected to contain the mapped input string.
     * @return true if only one macro was modified, which has now been analyzed again, false otherwise
     */
    fun trackMacroModification(
        prevFile: FileMappingInfo,
        newReader: DirectiveStringReader<AnalyzingResourceCreator>,
        modifiedRange: MacroModificationRange = getModifiedRange(prevFile, newReader.fileMappingInfo),
        isNested: Boolean = false
    ): Boolean {
        val oldMacros = newReader.resourceCreator.previousCache?.macroCache ?: return false
        val newMacros = newReader.resourceCreator.newCache.macroCache

        // Find the macro that encompasses the first change. If the change is exactly at the start of the macro, there was no modification inside the macro so return false
        var macroIndex = oldMacros.orderedMacroStartInParent.binarySearch { oldMacros.orderedMacroStartInParent[it].compareTo(modifiedRange.absoluteStartPosition) }
        if(macroIndex >= -1)
            return false // Exactly matched the start of a macro (>= 0) or no macro starts before this position (== -1)
        macroIndex = roundDownBinarySearch(macroIndex)
        val oldMacroNode = oldMacros.orderedMacros[macroIndex]
        val oldMacroAbsoluteEnd = oldMacroNode.fileRangeInParent.end
        if(oldMacroAbsoluteEnd < modifiedRange.absoluteStartPosition)
            return false // The position is after the end of the macro

        val oldMacroEndDist = prevFile.totalCharacters - oldMacroAbsoluteEnd
        if(oldMacroEndDist > modifiedRange.absoluteEndDist)
            return false // There's a change after the end of the macro

        // Parse the macro template
        val templateReader = if(isNested) {
            // The reader already contains the entire macro as a string, so just set the cursor
            newReader.copy().also {
                it.cursor = newReader.fileMappingInfo.cursorMapper.mapToTarget(oldMacroNode.fileRangeInParent.start)
            }
        } else {
            DirectiveStringReader.createReaderAtAbsoluteCursor(
                newReader.fileMappingInfo,
                newReader.dispatcher,
                newReader.resourceCreator,
                oldMacroNode.fileRangeInParent.start
            )
        }
        val newDecodedMacro = oldMacroNode.input.parser.parse(templateReader) ?: return false
        val newMacroEndDist = templateReader.fileMappingInfo.totalCharacters - newDecodedMacro.absoluteRange.end
        if(newMacroEndDist != oldMacroEndDist)
            return false // Something changed the range of the macro relative to the rest of the file. Can't use the cache.

        // Before analyzing the new macro, add all the macros before it to the new cache
        for(i in 0 until macroIndex) {
            val macro = oldMacros.orderedMacros[i]
            val modificationOffset = oldMacros.childModificationOffsets.get(i)
            newMacros.addMacro(macro)
            if(modificationOffset != null)
                newMacros.childModificationOffsets.put(i, modificationOffset)
        }

        // Analyze new macro. `analyzeMacroString` also checks if the modification is within a child and allows recursion
        val relevantLines = AnalyzingResult.getLinesBetweenCursors(newDecodedMacro.absoluteRange.start, newDecodedMacro.absoluteRange.end, templateReader.fileMappingInfo)
        val newInput = oldMacroNode.input.copy(lines = relevantLines)
        VanillaLanguage.analyzeMacroString(
            newInput,
            newDecodedMacro,
            oldMacroNode.children,
            newReader,
            newReader.resourceCreator.source,
            FileModificationData(
                oldMacroNode.updatedFile,
                oldMacroNode.analyzingResult,
                modifiedRange.relativeToChild(oldMacroNode.fileRangeInParent.start, oldMacroEndDist)
            )
        )
        val newCursorOffset = newReader.fileMappingInfo.totalCharacters - prevFile.totalCharacters
        val newFileOffset = AnalyzingResult.getPositionFromCursor(oldMacroAbsoluteEnd, prevFile)
            .differenceTo(AnalyzingResult.getPositionFromCursor(newDecodedMacro.absoluteRange.end, newReader.fileMappingInfo))
        val newOffset = AnalyzingResourceCreator.MacroOffset(newCursorOffset, newFileOffset)
        newMacros.childModificationOffsets.put(macroIndex, oldMacros.childModificationOffsets.get(macroIndex)?.addAfter(newOffset) ?: newOffset)
        if(newDecodedMacro.backgroundTokens != null)
            newMacros.childBackgroundSemanticTokens.put(macroIndex, newDecodedMacro.backgroundTokens)

        // Add all remaining cached macros with the new offset
        for(i in macroIndex + 1 until oldMacros.orderedMacros.size) {
            val macro = oldMacros.orderedMacros[i]
            val modificationOffset = oldMacros.childModificationOffsets.get(i)
            newMacros.addMacro(macro.withRange(macro.fileRangeInParent + (newDecodedMacro.absoluteRange.end - oldMacroAbsoluteEnd)))
            if(modificationOffset != null) {
                // This offset is still valid, because all modification offsets are added together by overlayMacros,
                // so only the offset for the macro that changed has to be modified
                newMacros.childModificationOffsets.put(i, modificationOffset)
            }
        }

        return true
    }

    private fun getModifiedRange(prevFile: FileMappingInfo, newFile: FileMappingInfo): MacroModificationRange =
        MacroModificationRange(
            getModifiedAbsoluteStart(prevFile, newFile),
            getModifiedAbsoluteEndDist(prevFile, newFile)
        )

    /**
     * Returns the amount of characters before the first change between the two files
     */
    private fun getModifiedAbsoluteStart(prevFile: FileMappingInfo, newFile: FileMappingInfo): Int {
        val prevLines = prevFile.lines
        val newLines = newFile.lines
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
        return absoluteStartPosition
    }
    /**
     * Returns the amount of characters after the last change between the two files.
     * Note that the position of the last change could be before the return value of [getModifiedAbsoluteStart],
     * for example if there are consecutive equal characters and the only change is that another equal character was added/removed.
     */
    private fun getModifiedAbsoluteEndDist(prevFile: FileMappingInfo, newFile: FileMappingInfo): Int {
        val prevLines = prevFile.lines
        val newLines = newFile.lines
        val minLineCount = min(prevLines.size, newLines.size)
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
        return absoluteEndDist
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

    data class FileModificationData(val oldFile: FileMappingInfo, val oldResult: AnalyzingResult, val modificationRange: MacroModificationRange)
    data class MacroModificationRange(val absoluteStartPosition: Int, val absoluteEndDist: Int) {
        fun relativeToChild(absoluteChildStartPos: Int, absoluteChildEndDist: Int) =
            MacroModificationRange(
                absoluteStartPosition - absoluteChildStartPos,
                absoluteEndDist - absoluteChildEndDist
            )
    }
}