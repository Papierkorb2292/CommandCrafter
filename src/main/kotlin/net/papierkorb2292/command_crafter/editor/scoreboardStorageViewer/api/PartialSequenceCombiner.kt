package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import java.util.*

class PartialSequenceCombiner<TPartial, TComplete>(
    private val isLast: (TPartial) -> Boolean,
    private val combiner: (List<TPartial>) -> TComplete,
) {
    private val consumedPartialsByRequestId = mutableMapOf<UUID, MutableList<TPartial>>()

    fun consumePartial(partial: TPartial, requestId: UUID): TComplete? {
        val consumedPartials = consumedPartialsByRequestId.getOrPut(requestId, ::ArrayList)
        consumedPartials += partial
        if(!isLast(partial))
            return null
        consumedPartialsByRequestId.remove(requestId)
        return combiner(consumedPartials)
    }
}