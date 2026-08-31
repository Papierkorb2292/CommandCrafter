package net.papierkorb2292.command_crafter.parser.helper

import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator

/**
 * This interface makes it possible to delay the analyzing of a node, when it is not yet known whether analyzing a node
 * is necessary. This is the case for macros, which only require a semantic tokens count at first. Note that implementation
 * that delay the analyzing need to make sure to restore any context back to the correct state (such as [AnalyzingResourceCreator.macroQueue]),
 * and submitted analyzers need to only depend on state that can be restored.
 *
 * Parsing of the input string should not be done through this, because it is required by the macro analyzer.
 * Only analyzing of the parsed data, besides generating simple semantic tokens, should be delayed.
 */
interface NodeAnalyzingExecutor {
    fun submit(analyzer: () -> Unit)

    object Immediate : NodeAnalyzingExecutor {
        override fun submit(analyzer: () -> Unit) {
            analyzer()
        }
    }
}