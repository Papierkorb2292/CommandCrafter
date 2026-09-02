package net.papierkorb2292.command_crafter.editor

import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.helper.WrappingExecutorService
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Future

class OpenFile(val uri: String, val lines: MutableList<StringBuilder>, var version: Int = 0) {
    val parsedUri = EditorURI.parseURI(uri)
    val cachedLineStrings: MutableList<String?> = lines.mapTo(ArrayList(lines.size)) { null }
    var currentAnalyzer: RunningAnalyzer? = null
    var runningAnalyzers = mutableSetOf<RunningAnalyzer>()
    var persistentAnalyzerData: Any? = null

    companion object {
        const val LINE_SEPARATOR = "\r\n"
        val analyzerExecutor = WrappingExecutorService.withErrorCallback(Executors.newFixedThreadPool(5)) { e ->
            CommandCrafter.LOGGER.error("Analyzer task threw error", e)
        }

        fun linesFromString(content: String) = linesFromStrings(content.lines())
        fun linesFromStrings(lines: List<String>): MutableList<StringBuilder> = lines.mapTo(ArrayList(lines.size), ::StringBuilder)
        fun fromString(uri: String, content: String, version: Int = 0) = fromLines(uri, content.lines(), version)
        fun fromLines(uri: String, lines: List<String>, version: Int = 0) = OpenFile(uri, lines.mapTo(ArrayList(lines.size), ::StringBuilder), version)
    }

    @Synchronized
    fun stringifyLines() = lines.mapIndexed { index, builder ->
        val cached = cachedLineStrings[index]
        if(cached != null) {
            cached
        } else {
            val string = builder.toString()
            cachedLineStrings[index] = string
            string
        }
    }
    fun createFileMappingInfo() = FileMappingInfo(stringifyLines())

    fun applyContentChange(change: TextDocumentContentChangeEvent) =
        applyContentChange(
            change.range.start.line,
            change.range.end.line,
            change.range.start.character,
            change.range.end.character,
            change.text
        )

    @Synchronized
    fun applyContentChange(
        startLine: Int,
        endLine: Int,
        startChar: Int,
        endChar: Int,
        newText: String,
    ) {
        if (startLine >= lines.size || endLine >= lines.size || startLine > endLine || (startLine == endLine && startChar > endChar)) {
            CommandCrafter.LOGGER.error("Received invalid incremental file modification: from ${startLine}:${startChar} to ${endLine}:${endChar}. Current line count: ${lines.size}")
            return
        }

        val newLines = newText.lineSequence().iterator()
        val startLineText = lines[startLine]
        val endLineText = lines[endLine]
        val secondLine = startLine + 1
        cachedLineStrings[startLine] = null
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
            var currentLine = secondLine
            do {
                val line = newLines.next()
                cachedLineStrings.add(currentLine, null)
                if(!newLines.hasNext()) {
                    lines.add(currentLine, StringBuilder(line).append(endText))
                    break
                }
                lines.add(currentLine++, StringBuilder(line))
            } while(true)
        } else {
            cachedLineStrings[endLine] = null
            startLineText.replace(startChar, startLineText.length, if (newLines.hasNext()) newLines.next() else "")
            if (!newLines.hasNext()) {
                //The start and end line have to be joined, since the new text has fewer than two lines
                startLineText.append(endLineText.substring(endChar))
                lines.subList(secondLine, endLine + 1).clear()
                cachedLineStrings.subList(secondLine, endLine + 1).clear()
                return
            }
            var currentLine = secondLine
            do {
                val line = newLines.next()
                if(!newLines.hasNext()) {
                    endLineText.replace(0, endChar, line)
                    break
                }
                if(currentLine < endLine) {
                    cachedLineStrings[currentLine] = null
                    lines[currentLine++] = StringBuilder(line)
                } else {
                    cachedLineStrings.add(currentLine, null)
                    lines.add(currentLine++, StringBuilder(line))
                }
            } while(true);
            if(currentLine < endLine) {
                lines.subList(currentLine, endLine).clear()
                cachedLineStrings.subList(currentLine, endLine).clear()
            }
        }
    }

    fun analyzeFile(languageServer: MinecraftLanguageServer): RunningAnalyzer? {
        val analyzer = startAnalyzingFile(languageServer) ?: return null
        analyzer.onNewDependent()
        return analyzer
    }

    fun startAnalyzingFile(languageServer: MinecraftLanguageServer): RunningAnalyzer? {
        val runningAnalyzer = currentAnalyzer
        if(runningAnalyzer != null)
            return runningAnalyzer
        for(analyzer in MinecraftLanguageServer.analyzers) {
            if(analyzer.canHandle(this)) {
                val version = version

                val completableFuture = CompletableFuture<AnalyzingResult>()
                val future = analyzer.analyzeAsync(this, languageServer, analyzerExecutor, completableFuture)
                val runningAnalyzer = RunningAnalyzer(future, completableFuture, 0)
                currentAnalyzer = runningAnalyzer
                runningAnalyzers += runningAnalyzer
                completableFuture.thenRun {
                    runningAnalyzers -= runningAnalyzer
                }
                completableFuture.thenAccept { result ->
                    if(this.version == version) {
                        MinecraftLanguageServer.fillDiagnosticsSource(result.diagnostics)
                        languageServer.client?.publishDiagnostics(PublishDiagnosticsParams(uri, result.diagnostics, version))
                    }
                }
                return runningAnalyzer
            }
        }
        return null
    }

    fun stopAnalyzing(forceCancel: Boolean = false) {
        runningAnalyzers.forEach {
            if(forceCancel || it.dependents == 0)
                it.future.cancel(true)
        }
        runningAnalyzers.clear()
        currentAnalyzer = null
    }

    fun <T> registerAnalyzerCancel(analyzer: RunningAnalyzer, future: CompletableFuture<T>): CompletableFuture<T> {
        future.whenComplete { _, throwable ->
            if(throwable is CancellationException)
                if(analyzer.onDependentCancelled(analyzer != currentAnalyzer))
                    runningAnalyzers -= analyzer
        }
        // Make sure to return the original future, because that's the one that LSP4J will complete with a CancellationException.
        // Completing the future returned by `whenComplete` wouldn't trigger the callback.
        return future
    }

    class RunningAnalyzer(val future: Future<*>, val result: CompletableFuture<AnalyzingResult>, var dependents: Int) {
        fun onNewDependent() {
            dependents++
        }

        fun onDependentCancelled(canCancelAnalyzer: Boolean): Boolean {
            if(--dependents == 0 && canCancelAnalyzer) {
                future.cancel(true)
                return true
            }
            return false
        }
    }
}