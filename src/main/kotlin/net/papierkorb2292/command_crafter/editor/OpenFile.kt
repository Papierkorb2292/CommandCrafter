package net.papierkorb2292.command_crafter.editor

import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import java.util.concurrent.CompletableFuture

class OpenFile(val uri: String, val lines: MutableList<StringBuffer>, var version: Int = 0) {
    val parsedUri = EditorURI.parseURI(uri)
    var analyzingResult: CompletableFuture<AnalyzingResult>? = null

    companion object {
        const val LINE_SEPARATOR = "\r\n"

        fun linesFromString(content: String) = linesFromStrings(content.lines())
        fun linesFromStrings(lines: List<String>): MutableList<StringBuffer> = lines.mapTo(ArrayList(lines.size), ::StringBuffer)
        fun fromString(uri: String, content: String, version: Int = 0) = fromLines(uri, content.lines(), version)
        fun fromLines(uri: String, lines: List<String>, version: Int = 0) = OpenFile(uri, lines.mapTo(ArrayList(lines.size), ::StringBuffer), version)
    }

    fun stringifyLines() = lines.map { it.toString() }
    fun createFileMappingInfo() = FileMappingInfo(stringifyLines())

    fun applyContentChange(change: TextDocumentContentChangeEvent) =
        applyContentChange(
            change.range.start.line,
            change.range.end.line,
            change.range.start.character,
            change.range.end.character,
            change.text
        )

    fun applyContentChange(
        startLine: Int,
        endLine: Int,
        startChar: Int,
        endChar: Int,
        newText: String,
    ) {
        if (startLine >= lines.size || endLine >= lines.size || startLine > endLine || (startLine == endLine && startChar > endChar)) {
            return
        }

        fun addNewLinesAndReturnLast(lines: MutableList<StringBuffer>, newLines: Iterator<String>, start: Int): String {
            var currentLine = start
            for (line in newLines) {
                if (!newLines.hasNext()) return line
                lines.add(currentLine++, StringBuffer(line))
            }
            return ""
        }

        val newLines = newText.lineSequence().iterator()
        val startLineText = lines[startLine]
        val endLineText = lines[endLine]
        val secondLine = startLine + 1
        if (startLine == endLine) {
            if (!newLines.hasNext()) {
                startLineText.delete(startChar, endChar)
                return
            }
            val firstLineText = newLines.next()
            if (!newLines.hasNext()) {
                startLineText.replace(startChar, endChar, firstLineText)
                return
            }
            //The line needs to be split up, because the new text consists of multiple lines
            val endText = startLineText.substring(endChar)
            startLineText.replace(startChar, startLineText.length, firstLineText)
            val preAddLineCount = lines.size
            val last = addNewLinesAndReturnLast(lines, newLines, secondLine)
            lines.add(secondLine + lines.size - preAddLineCount, StringBuffer(last).append(endText))
        } else {
            startLineText.replace(startChar, startLineText.length, if (newLines.hasNext()) newLines.next() else "")
            if (endLine > secondLine) {
                lines.subList(secondLine, endLine).clear()
            }
            if (!newLines.hasNext()) {
                //The start and end line have to be joined, since the new text has fewer than two lines
                startLineText.append(endLineText.substring(endChar))
                //The last line moved to secondLine, as everything between
                //was removed in the previous 'if' statement
                lines.removeAt(secondLine)
                return
            }
            val last = addNewLinesAndReturnLast(lines, newLines, secondLine)
            endLineText.replace(0, endChar, last)
        }
    }

    fun analyzeFile(languageServer: MinecraftLanguageServer): CompletableFuture<AnalyzingResult>? {
        val runningAnalyzer = analyzingResult
        if(runningAnalyzer != null)
            return runningAnalyzer
        for(analyzer in MinecraftLanguageServer.analyzers) {
            if(analyzer.canHandle(this)) {
                val version = version
                return CompletableFuture.supplyAsync {
                    analyzer.analyze(this, languageServer)
                }.also { newRunningAnalyzer ->
                    analyzingResult = newRunningAnalyzer
                    newRunningAnalyzer.thenAccept {
                        if(this.version != version)
                            return@thenAccept
                        MinecraftLanguageServer.fillDiagnosticsSource(it.diagnostics)
                        languageServer.client?.publishDiagnostics(PublishDiagnosticsParams(uri, it.diagnostics, version))
                    }
                }
            }
        }
        return null
    }
}