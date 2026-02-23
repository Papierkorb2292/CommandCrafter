package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.serialization.DataResult

/**
 * Only calls the delegate for the start and result of the first decoder that a node is passed.
 */
class FirstDecoderResultCallback(val delegate: PreLaunchDecoderOutputTracker.ResultCallback): PreLaunchDecoderOutputTracker.ResultCallback {
    private val decodeStackCounts = mutableMapOf<Any?, Int>()
    private fun postDecrementDecodeStackCount(node: Any?): Int {
        val count = decodeStackCounts[node]!!
        if(count == 1)
            decodeStackCounts.remove(node)
        else
            decodeStackCounts[node] = count - 1

        return count
    }

    override fun <TInput, TResult> onError(error: DataResult.Error<TResult>, input: TInput) {
        if(postDecrementDecodeStackCount(input) > 1) return
        delegate.onError(error, input)
    }

    override fun <TInput> markStringParseError(input: TInput) {
        delegate.markStringParseError(input)
    }

    override fun <TInput, TResult> onResult(result: TResult, isPartial: Boolean, input: TInput) {
        if(postDecrementDecodeStackCount(input) > 1) return
        delegate.onResult(result, isPartial, input)
    }

    override fun <TInput> onDecodeStart(input: TInput) {
        val newCount = decodeStackCounts.getOrDefault(input, 0) + 1
        decodeStackCounts[input] = newCount
        if(newCount > 1) return
        delegate.onDecodeStart(input)
    }
}