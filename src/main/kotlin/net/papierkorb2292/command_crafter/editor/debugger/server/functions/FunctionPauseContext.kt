package net.papierkorb2292.command_crafter.editor.debugger.server.functions

import com.mojang.brigadier.ParseResults
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.CommandContextBuilder
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.function.CommandFunction
import net.minecraft.server.function.CommandFunctionManager
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseHandler
import net.papierkorb2292.command_crafter.editor.debugger.variables.VariablesReferenceMapper
import java.util.*

/**
 * Holds information about the current state of a command function
 * execution that is paused. Every command is split up into sections,
 * where every section corresponds to a subcommand, which Minecraft
 * represents with a [CommandContextBuilder], whose child is the next section.
 *
 * Implemented by [FunctionPauseContextImpl].
 */
sealed interface FunctionPauseContext : VariablesReferenceMapper {
    val executedEntries: List<CommandFunctionManager.Entry>
    val executionQueue: Deque<CommandFunctionManager.Entry>
    val currentCommand: CommandFunction.CommandElement

    /**
     * A stack of [SectionContexts] representing the stack trace of the currently viewed context.
     * The stack can contain more elements beyond that, in case more contexts have already been executed,
     * but [SectionContexts.currentContextIndex] may only be valid up until [FunctionPauseContext.indexOfCurrentSectionInContextStack]
     *
     * During [DebugPauseHandler.shouldStopOnCurrentContext], currentContextIndex is the same as the last pause
     * up until the value of [FunctionPauseContext.indexOfCurrentSectionInContextStack] when the debugger tried
     * to pause prior to the current invocation. New elements of the stack still represent the current pause
     * and in case no new section was entered between the last and the current pause, the current context index
     * only has to be increased by one to get the current context of the current pause instead of the previous pause.
     */
    val contextStack: List<SectionContexts>
    val currentSectionIndex: Int
    val indexOfCurrentSectionInContextStack: Int

    fun pauseAtCommandSection(command: ParseResults<ServerCommandSource>, sectionIndex: Int)
    fun stepIntoFunctionCall()
    fun stepOutOfFunction()
    fun continue_()

    /**
     * Holds information about all contexts that a particular command section (subcommand) is invoked on.
     * @param contexts The contexts that the command section is invoked on
     * @param branchContextGroupsEndIndices The indices of the last context of each group of contexts
     *  that have been produced by the same context in the previous command section
     * @param currentContextIndex The index of the current context that is shown by the debugger
     */
    class SectionContexts(val contexts: MutableList<CommandContext<ServerCommandSource>>, val branchContextGroupsEndIndices: List<Int>, var currentContextIndex: Int) {
        var currentContext
            get() = contexts[currentContextIndex]
            set(value) {
                contexts[currentContextIndex] = value
            }

        fun hasNext() = currentContextIndex + 1 < contexts.size
        fun advancedExists() = ++currentContextIndex < contexts.size
        fun isAdvancedInSameGroup() = areInSameGroup(currentContextIndex, ++currentContextIndex)
        fun hasNextInSameGroup()
            = hasNext() && areInSameGroup(currentContextIndex, currentContextIndex + 1)
        fun areInSameGroup(firstContext: Int, secondIndex: Int): Boolean {
            if(firstContext == secondIndex) return true
            val smallerIndex: Int
            val largerIndex: Int
            if(firstContext < secondIndex) {
                smallerIndex = firstContext
                largerIndex = secondIndex
            } else {
                smallerIndex = secondIndex
                largerIndex = firstContext
            }
            for (i in branchContextGroupsEndIndices) {
                if (i >= smallerIndex) {
                    return i >= largerIndex
                }
            }
            return false
        }
        fun getGroupIndexOfContext(contextIndex: Int): Int? {
            branchContextGroupsEndIndices.forEachIndexed { index, i ->
                if (i >= contextIndex) {
                    return index
                }
            }
            return null
        }
        fun isContextInGroup(contextIndex: Int, groupIndex: Int): Boolean {
            val groupEndIndex = branchContextGroupsEndIndices[groupIndex]
            return if (groupIndex == 0) {
                contextIndex <= groupEndIndex
            } else {
                val previousGroupEndIndex = branchContextGroupsEndIndices[groupIndex - 1]
                contextIndex in (previousGroupEndIndex + 1)..groupEndIndex
            }
        }
    }
}