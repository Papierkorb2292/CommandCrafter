package net.papierkorb2292.command_crafter.editor.processing.codecmod

class DecoderPossibleValueTracker<TNode : Any> : ExtraDecoderBehavior<TNode> {
    override val branchBehavior: ExtraDecoderBehavior.BranchBehavior
        get() = ExtraDecoderBehavior.BranchBehavior.ALL_POSSIBLE_ENCODED

    val possibleValues = mutableMapOf<TNode, MutableList<TNode>>()

    override fun notePossibleValues(
        input: TNode,
        provider: ExtraDecoderBehavior.PossibleValue.Provider<TNode>,
        shouldSuggest: Boolean
    ) {
        possibleValues.getOrPut(input, ::mutableListOf) += provider.getValue().map { it.element }.toList()
    }
}