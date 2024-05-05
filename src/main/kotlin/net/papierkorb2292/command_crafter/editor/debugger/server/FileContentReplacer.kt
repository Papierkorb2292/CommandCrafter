package net.papierkorb2292.command_crafter.editor.debugger.server

import com.mojang.brigadier.context.StringRange
import net.papierkorb2292.command_crafter.editor.OpenFile
import net.papierkorb2292.command_crafter.editor.debugger.helper.Positionable
import net.papierkorb2292.command_crafter.parser.helper.CombinedProcessedInputCursorMapper
import net.papierkorb2292.command_crafter.parser.helper.OffsetProcessedInputCursorMapper
import net.papierkorb2292.command_crafter.parser.helper.ProcessedInputCursorMapper
import kotlin.math.min

interface FileContentReplacer {
    companion object {
        fun concatReplacementData(data: List<ReplacementDataProvider>): ReplacementDataProvider? {
            if(data.isEmpty()) return null
            if(data.size == 1) return data.single()
            val replacements = data.flatMap { it.replacements }.asSequence()
            val positionables = data.flatMap { it.positionables }.asSequence()
            return ReplacementDataProvider(replacements, positionables) { sourceReference ->
                data.forEach { it.sourceReferenceCallback(sourceReference) }
            }
        }
    }

    fun getReplacementData(path: String): ReplacementDataProvider?

    data class ReplacementDataProvider(
        val replacements: Sequence<Replacement>,
        val positionables: Sequence<Positionable>,
        val sourceReferenceCallback: (Int) -> Unit,
    )

    data class Replacement(
        val startLine: Int,
        val startChar: Int,
        val endLine: Int,
        val endChar: Int,
        val string: String,
        val cursorMapper: ProcessedInputCursorMapper
    )

    class Document(val lines: List<String>, val positionables: Sequence<Positionable>) {

        fun applyReplacements(replacements: Sequence<Replacement>): Pair<Document, ProcessedInputCursorMapper> {
            if(!replacements.iterator().hasNext()) return this to OffsetProcessedInputCursorMapper(0)
            val replacementsSorted = replacements.sortedWith { a, b ->
                val lineCmp = a.startLine.compareTo(b.startLine)
                if(lineCmp != 0) lineCmp
                else a.startChar.compareTo(b.startChar)
            }.toList()
            val positionablesSorted = positionables.sortedWith { a, b ->
                val lineCmp = a.line.compareTo(b.line)
                if(lineCmp != 0) lineCmp
                else a.char.compareTo(b.char)
            }.toList()
            var positionablesIndex = 0

            var currentCursorMappingSourceChar = 0
            var currentCursorMappingTargetChar = 0

            val resultCursorMapperEntries = mutableListOf<CombinedProcessedInputCursorMapper.Entry>()

            val resultLines = mutableListOf("")
            val resultPositionables = mutableListOf<Positionable>()

            fun addExisting(startLine: Int, endLine: Int, startChar: Int, endChar: Int) {
                while(positionablesIndex < positionablesSorted.size) {
                    val pos = positionablesSorted[positionablesIndex]
                    if(pos.line > endLine + 1 || (pos.line == endLine + 1 && pos.char >= endChar + 1))
                        break
                    if(pos.line < startLine + 1 || (pos.line == startLine + 1 && pos.char < startChar + 1)) {
                        positionablesIndex++
                        continue
                    }
                    pos.setPos(
                        pos.line - startLine + resultLines.size - 1,
                        pos.char - if(pos.line == startLine) startChar + resultLines[startLine].length else 0
                    )
                    resultPositionables += pos
                    positionablesIndex++
                }
                var contentLength = 0
                val startLineString = lines[startLine]
                if(startLine == endLine) {
                    val content = startLineString.substring(startChar, endChar)
                    resultLines[resultLines.lastIndex] += content
                    contentLength += content.length
                } else {
                    contentLength += startLineString.length - startChar + 1
                    resultLines[resultLines.lastIndex] += startLineString.substring(startChar)
                    for(i in startLine + 1 until endLine) {
                        val currentLine = lines[i]
                        resultLines.add(currentLine)
                        contentLength += currentLine.length + 1
                    }
                    resultLines.add(lines[endLine].substring(0, endChar))
                    contentLength += endChar
                }
                resultCursorMapperEntries += CombinedProcessedInputCursorMapper.Entry(
                    StringRange(currentCursorMappingSourceChar, currentCursorMappingSourceChar + contentLength),
                    StringRange(currentCursorMappingTargetChar, currentCursorMappingTargetChar + contentLength),
                    OffsetProcessedInputCursorMapper(currentCursorMappingTargetChar - currentCursorMappingSourceChar)
                )
                currentCursorMappingSourceChar += contentLength
                currentCursorMappingTargetChar += contentLength
            }
            fun addReplacement(replacement: Replacement) {
                val lines = replacement.string.split('\n').toMutableList()
                var countedLength = 0
                var startLine = replacement.startLine
                var startChar = replacement.startChar
                var addedLength = 0
                fun addUntilLength(length: Int): Boolean {
                    while(addedLength < length) {
                        val line = lines.removeFirstOrNull() ?: return false
                        if(line.length + addedLength > length) {
                            val char = length - addedLength
                            val content = line.substring(0, char)
                            lines.add(0, line.substring(char))
                            resultLines[resultLines.lastIndex] += content
                            addedLength += content.length
                            return true
                        }
                        resultLines[resultLines.lastIndex] += line
                        addedLength += line.length + 1
                    }
                    return true
                }
                while(positionablesIndex < positionablesSorted.size) {
                    val pos = positionablesSorted[positionablesIndex]
                    val posLine = pos.line - 1
                    val posChar = pos.char - 1
                    if(posLine > replacement.endLine || (posLine == replacement.endLine && posChar >= replacement.endChar))
                        break
                    if(posLine < replacement.startLine || (posLine == replacement.startLine && posChar < replacement.startChar)) {
                        positionablesIndex++
                        continue
                    }
                    countedLength += countCharsBetweenPos(
                        startLine, startChar, posLine, posChar, 1
                    )
                    startLine = posLine
                    startChar = posChar
                    val posTargetCursor = replacement.cursorMapper.mapToTarget(countedLength, true)
                    require(posTargetCursor >= 0) { "Replacement CursorMapper mapped positionable to before the replacement (positionable: $pos)" }
                    require(posTargetCursor >= addedLength) { "Replacement CursorMapper is not allowed to change order of cursors (happened when mapping positionable: $pos)" }
                    require(addUntilLength(posTargetCursor)) { "Replacement CursorMapper mapped positionable to after the replacement (positionable: $pos)" }
                    pos.setPos(
                        resultLines.size,
                        resultLines.last().length + 1
                    )
                    resultPositionables += pos
                    positionablesIndex++
                }
                countedLength += countCharsBetweenPos(
                    startLine, startChar, replacement.endLine, replacement.endChar, 1
                )
                resultCursorMapperEntries += CombinedProcessedInputCursorMapper.Entry(
                    StringRange(currentCursorMappingSourceChar, currentCursorMappingSourceChar + countedLength),
                    StringRange(currentCursorMappingTargetChar, currentCursorMappingTargetChar + replacement.string.length),
                    replacement.cursorMapper
                )
                currentCursorMappingTargetChar += replacement.string.length
                currentCursorMappingSourceChar += countedLength
                addUntilLength(replacement.string.length)
            }

            val first = replacementsSorted.first()
            addExisting(0, first.startLine, 0, first.startChar)
            for(i in 0 until replacementsSorted.size - 1) {
                val replacement = replacementsSorted[i]
                val nextRepl = replacementsSorted[i + 1]
                require(!(replacement.endLine > nextRepl.startLine || (replacement.endLine == nextRepl.startLine && replacement.endChar > nextRepl.startChar))) {
                    "Replacements must not overlap: $replacement and $nextRepl"
                }
                addReplacement(replacement)
                addExisting(replacement.endLine, nextRepl.startLine, replacement.endChar + 1, nextRepl.startChar)
            }
            val last = replacementsSorted.last()
            addReplacement(last)
            val endChar = lines.last().length
            addExisting(last.endLine, lines.size - 1, min(last.endChar + 1, endChar), endChar)
            return Document(resultLines, resultPositionables.asSequence()) to CombinedProcessedInputCursorMapper(resultCursorMapperEntries)
        }

        fun concatLines(lineSeparator: String = OpenFile.LINE_SEPARATOR): String {
            val separatorLength = lineSeparator.length
            val length = lines.sumOf { it.length + separatorLength } - separatorLength
            val builder = StringBuilder(length)

            for(i in 0 until lines.size - 1) {
                builder.append(lines[i]).append(lineSeparator)
            }
            builder.append(lines.last())

            return builder.toString()
        }

        fun countCharsBetweenPos(startLine: Int, startChar: Int, endLine: Int, endChar: Int, lineSeparatorLength: Int): Int {
            if(startLine > endLine) throw IllegalArgumentException("startLine must be <= endLine")
            if(startLine == endLine) {
                if(startChar > endChar) throw IllegalArgumentException("startChar must be <= endChar if startLine == endLine")
                return endChar - startChar
            }
            val startLineLength = lines[startLine].length + lineSeparatorLength - startChar
            val betweenLineLengths = (startLine + 1 until endLine).sumOf { lines[it].length + lineSeparatorLength }
            return startLineLength + betweenLineLengths + endChar
        }
    }
}