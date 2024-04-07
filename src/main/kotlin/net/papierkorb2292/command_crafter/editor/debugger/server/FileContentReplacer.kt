package net.papierkorb2292.command_crafter.editor.debugger.server

import net.papierkorb2292.command_crafter.editor.OpenFile
import net.papierkorb2292.command_crafter.editor.debugger.helper.Positionable
import net.papierkorb2292.command_crafter.parser.helper.ProcessedInputCursorMapper

interface FileContentReplacer {
    companion object {
        fun concatReplacementData(data: List<ReplacementDataProvider>): ReplacementDataProvider? {
            if(data.isEmpty()) return null
            if(data.size == 1) return data.single()
            val replacings = data.flatMap { it.replacings }.asSequence()
            val positionables = data.flatMap { it.positionables }.asSequence()
            return ReplacementDataProvider(replacings, positionables) { sourceReference ->
                data.forEach { it.sourceReferenceCallback(sourceReference) }
            }
        }
    }

    fun getReplacementData(path: String): ReplacementDataProvider?

    data class ReplacementDataProvider(
        val replacings: Sequence<Replacing>,
        val positionables: Sequence<Positionable>,
        val sourceReferenceCallback: (Int) -> Unit,
    )

    data class Replacing(
        val startLine: Int,
        val startChar: Int,
        val endLine: Int,
        val endChar: Int,
        val replacement: String
    )

    class Document(val lines: List<String>, val positionables: Sequence<Positionable>) {

        fun applyReplacings(replacings: Sequence<Replacing>): Pair<Document, ProcessedInputCursorMapper> {
            if(!replacings.iterator().hasNext()) return this to ProcessedInputCursorMapper()
            val replacingsSorted = replacings.sortedWith { a, b ->
                val lineCmp = a.startLine.compareTo(b.startLine)
                if(lineCmp != 0) lineCmp
                else a.startChar.compareTo(b.startChar)
            }.toList()
            val positionablesSorted = positionables.sortedWith { a, b ->
                val lineCmp = a.line.compareTo(b.line)
                if(lineCmp != 0) lineCmp
                else a.char?.compareTo(b.char ?: 0) ?: 0
            }.toList()
            var positionablesIndex = 0

            var currentCursorMappingLength = 0
            var currentCursorMappingSourceChar = 0
            var currentCursorMappingTargetChar = 0

            val resultCursorMapper = ProcessedInputCursorMapper()

            fun addCursorMapping() {
                resultCursorMapper.addMapping(
                    currentCursorMappingSourceChar,
                    currentCursorMappingTargetChar,
                    currentCursorMappingLength
                )
                currentCursorMappingSourceChar += currentCursorMappingLength
                currentCursorMappingTargetChar += currentCursorMappingLength
                currentCursorMappingLength = 0
            }

            val resultLines = mutableListOf<String>()
            val resultPositionables = mutableListOf<Positionable>()

            fun addExisting(startLine: Int, endLine: Int, startChar: Int, endChar: Int) {
                while(positionablesIndex < positionablesSorted.size) {
                    val pos = positionablesSorted[positionablesIndex]
                    if(pos.line > endLine || (pos.line == endLine && pos.char?.let { it >= endChar } == true))
                        break
                    if(pos.line < startLine || (pos.line == startLine && pos.char?.let { it < startChar} == true)) {
                        positionablesIndex++
                        continue
                    }
                    pos.setPos(
                        pos.line - startLine + resultLines.size,
                        pos.char?.let { it - if(pos.line == startLine) startChar + resultLines[startLine].length else 0 }
                    )
                    resultPositionables += pos
                    positionablesIndex++
                }
                val startLineString = lines[startLine]
                currentCursorMappingLength += startLineString.length - startChar + 1
                if(resultLines.isEmpty()) {
                    resultLines.add(startLineString.substring(startChar))
                } else {
                    resultLines[resultLines.lastIndex] += startLineString.substring(startChar)
                }
                for(i in startLine + 1 until endLine) {
                    val currentLine = lines[i]
                    resultLines.add(currentLine)
                    currentCursorMappingLength += currentLine.length + 1
                }
                resultLines.add(lines[endLine].substring(0, endChar))
                currentCursorMappingLength += endChar
            }
            fun addReplacement(replacing: Replacing) {
                val replacement = replacing.replacement
                addCursorMapping()
                currentCursorMappingTargetChar += replacement.length
                currentCursorMappingSourceChar += countCharsBetweenPos(
                    replacing.startLine, replacing.startChar, replacing.endLine, replacing.endChar, 1
                )
                val lines = replacement.splitToSequence('\n')
                if(resultLines.isEmpty()) {
                    resultLines.addAll(lines)
                    return
                }
                val first = lines.firstOrNull() ?: return
                resultLines[resultLines.lastIndex] += first
                resultLines.addAll(lines.drop(1))
            }

            val first = replacingsSorted.first()
            addExisting(0, first.startLine, 0, first.startChar)
            for(i in 0 until replacingsSorted.size - 1) {
                val replacing = replacingsSorted[i]
                val nextRepl = replacingsSorted[i + 1]
                if(replacing.endLine > nextRepl.startLine || (replacing.endLine == nextRepl.startLine && replacing.endChar > nextRepl.startChar)) {
                    throw IllegalArgumentException("Replacings must not overlap")
                }
                addReplacement(replacing)
                addExisting(replacing.endLine, nextRepl.startLine, replacing.endChar, nextRepl.startChar)
            }
            val last = replacingsSorted.last()
            addReplacement(last)
            addExisting(last.endLine, lines.size - 1, last.endChar, lines.last().length)
            addCursorMapping()
            return Document(resultLines, resultPositionables.asSequence()) to resultCursorMapper
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