package net.papierkorb2292.command_crafter.editor.processing.helper

import com.mojang.datafixers.util.Either
import com.mojang.serialization.Decoder
import com.mojang.serialization.DynamicOps
import net.minecraft.nbt.NbtElement
import net.minecraft.util.packrat.ParsingStateImpl
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree
import net.papierkorb2292.command_crafter.helper.getOrNull
import net.papierkorb2292.command_crafter.parser.helper.RawResource
import java.util.HashMap
import java.util.WeakHashMap

object PackratParserAdditionalArgs {
    val analyzingResult = ThreadLocal<AnalyzingResultBranchingArgument>()
    val furthestAnalyzingResult = ThreadLocal<Pair<Int, AnalyzingResult?>>()
    val stringifiedArgument = ThreadLocal<StringifiedBranchingArgument>()
    val nbtStringRangeTreeBuilder = ThreadLocal<StringRangeTreeBranchingArgument<NbtElement>>()
    val allowMalformed = ThreadLocal<Boolean>()

    private val branchingArgs = listOf(
        BranchingArgumentContainer(analyzingResult, ::AnalyzingResultBranchingArgument),
        BranchingArgumentContainer(stringifiedArgument, ::StringifiedBranchingArgument),
        BranchingArgumentContainer(nbtStringRangeTreeBuilder, ::StringRangeTreeBranchingArgument)
    )

    val delayedDecodeNbtAnalyzeCallback = ThreadLocal<(DynamicOps<NbtElement>, Decoder<*>) -> Unit>()

    fun hasArgs() = branchingArgs.any { it.argument.getOrNull() != null}

    fun branchAllArgs(): (successful: Boolean) -> Unit {
        val branchCallbacks = branchingArgs.map { it.branchArg() }
        return {
            for (callback in branchCallbacks)
                callback(it)
        }
    }

    fun shouldAllowMalformed() = allowMalformed.getOrNull() ?: false

    fun storeFurthestAnalyzingResult(cursor: Int) {
        val analyzingResult = analyzingResult.getOrNull()?.analyzingResult ?: return
        val currentFurthest = furthestAnalyzingResult.getOrNull() ?: return
        if(cursor >= currentFurthest.first) furthestAnalyzingResult.set(cursor to analyzingResult)
    }

    fun setupFurthestAnalyzingResultStart() {
        // Set a default value, which means addFurthestAnalyzingResult can from now on replace it with actual analyzing results
        furthestAnalyzingResult.set(0 to null)
    }

    fun getAndRemoveFurthestAnalyzingResult(): AnalyzingResult? {
        val result = furthestAnalyzingResult.get()
        furthestAnalyzingResult.remove()
        return result?.second
    }

    interface BranchingArgument<TArg> {
        fun createBranch(): TArg
        fun mergeBranch(argument: TArg, success: Boolean)
    }

    class BranchingArgumentContainer<TArg, TBranch : BranchingArgument<TArg>>(
        val argument: ThreadLocal<TBranch>,
        val argumentWrapper: (TArg) -> TBranch,
    ) {
        fun branchArg(): (successful: Boolean) -> Unit {
            val previousBranch = argument.getOrNull() ?: return {}
            val newBranch = previousBranch.createBranch()
            argument.set(argumentWrapper(newBranch))
            return {
                previousBranch.mergeBranch(newBranch, it)
                argument.set(previousBranch)
            }
        }
    }

    data class AnalyzingResultBranchingArgument(var analyzingResult: AnalyzingResult) : BranchingArgument<AnalyzingResult> {
        private var mergedBranchCount = 0
        override fun createBranch() = analyzingResult.copyExceptCompletions()
        override fun mergeBranch(argument: AnalyzingResult, success: Boolean) {
            if(success)
                analyzingResult = argument.copyExceptCompletions()
            // Completions are copied separately even if the branch was successful because the mergedBranchCount is not copied from the branch,
            // which could otherwise lead to duplicate completion names if the branch had a higher count
            analyzingResult.combineWithCompletionProviders(argument, (mergedBranchCount++).toString())
        }
    }

    data class StringifiedBranchingArgument(var stringified: MutableList<Either<String, RawResource>>) : BranchingArgument<MutableList<Either<String, RawResource>>> {
        override fun createBranch() = ArrayList(stringified)
        override fun mergeBranch(argument: MutableList<Either<String, RawResource>>, success: Boolean) {
            if(success)
                stringified = argument.toMutableList()
        }
    }

    data class StringRangeTreeBranchingArgument<TNode: Any>(val stringRangeTreeBuilder: StringRangeTree.PartialBuilder<TNode>) : BranchingArgument<StringRangeTree.PartialBuilder<TNode>> {
        override fun createBranch() = stringRangeTreeBuilder.pushBuilder()
        override fun mergeBranch(argument: StringRangeTree.PartialBuilder<TNode>, success: Boolean) {
            if(success)
                stringRangeTreeBuilder.popBuilder(argument)
        }
    }
}