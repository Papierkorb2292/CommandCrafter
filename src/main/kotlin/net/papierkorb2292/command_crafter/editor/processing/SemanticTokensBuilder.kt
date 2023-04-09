package net.papierkorb2292.command_crafter.editor.processing

import org.eclipse.lsp4j.SemanticTokens

class SemanticTokensBuilder {
    private val data = ArrayList<Int>(100)
    private var dataSize = 100
    private var lastLine = 0
    private var lastCursor = 0

    fun add(line: Int, cursor: Int, length: Int, type: TokenType, modifiers: Int) {
        if(data.size >= dataSize) {
            dataSize += 100
            data.ensureCapacity(dataSize)
        }
        data.add(line - lastLine)
        if(lastLine != line) {
            lastLine = line
            lastCursor = 0
            data.add(cursor)
        } else {
            data.add(cursor - lastCursor)
        }
        data.add(length)
        data.add(type.id)
        data.add(modifiers)
        lastCursor = cursor
    }

    fun fill(tokens: SemanticTokens, resultId: String? = null) {
        tokens.data = data
        if(resultId != null)
            tokens.resultId = resultId
    }
}