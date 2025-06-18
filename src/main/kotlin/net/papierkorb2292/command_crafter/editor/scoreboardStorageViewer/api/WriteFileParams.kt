package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import io.netty.buffer.ByteBufUtil
import kotlin.math.min

class WriteFileParams(
    var uri: String,
    var contentBase64: String
) {
    companion object {
        fun fromPartial(partials: List<PartialWriteFileParams>): WriteFileParams {
            val firstPartial = partials.first()
            val combined = WriteFileParams(firstPartial.uri, firstPartial.contentBase64)
            for(partial in partials.subList(1, partials.size)) {
                require(combined.uri == partial.uri) { "Uris of combined PartialWriteFileParams didn't match" }
                combined.contentBase64 += partial.contentBase64
            }
            return combined
        }
    }

    constructor() : this("", "")

    fun toPartial(): List<PartialWriteFileParams> {
        val maxStringByteCount = 32000 // Maximum is 32767, leaving some space
        val maxContentSize = maxStringByteCount - ByteBufUtil.utf8Bytes(uri)

        val partialParams = mutableListOf<PartialWriteFileParams>()
        var contentBase64: CharSequence = contentBase64
        do {
            val coveredLength = min(contentBase64.length, maxContentSize)
            partialParams += PartialWriteFileParams(
                uri,
                contentBase64.substring(0, coveredLength),
                coveredLength == contentBase64.length
            )
            contentBase64 = contentBase64.subSequence(coveredLength, contentBase64.length)
        } while(contentBase64.isNotEmpty())

        return partialParams
    }
}