package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import kotlin.math.min

class ReadFileResult(var contentBase64: String) {
    companion object {
        fun fromPartial(partials: List<PartialReadFileResult>): ReadFileResult {
            return ReadFileResult(partials.map { it.contentBase64 }.reduce(String::plus))
        }
    }
    
    fun toPartial(): List<PartialReadFileResult> {
        val maxStringByteCount = 32000 // Maximum is 32767, leaving some space
        val maxContentSize = maxStringByteCount

        val partialResults = mutableListOf<PartialReadFileResult>()
        var contentBase64: CharSequence = contentBase64
        do {
            val coveredLength = min(contentBase64.length, maxContentSize)
            partialResults += PartialReadFileResult(
                contentBase64.substring(0, coveredLength),
                coveredLength == contentBase64.length
            )
            contentBase64 = contentBase64.subSequence(coveredLength, contentBase64.length)
        } while(contentBase64.isNotEmpty())

        return partialResults
    }
}