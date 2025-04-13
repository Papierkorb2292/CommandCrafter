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

    fun temporarilyClearArgs(): () -> Unit {
        val analyzingResultVal = analyzingResult.getOrNull()
        analyzingResult.remove()
        val stringifiedArgumentVal = stringifiedArgument.getOrNull()
        stringifiedArgument.remove()
        val nbtStringRangeTreeBuilderVal = nbtStringRangeTreeBuilder.getOrNull()
        nbtStringRangeTreeBuilder.remove()
        val allowMalformedVal = allowMalformed.getOrNull()
        allowMalformed.remove()
        val furthestAnalyzingResultVal = furthestAnalyzingResult.getOrNull()
        furthestAnalyzingResult.remove()
        val delayedDecodeNbtAnalyzeCallbackVal = delayedDecodeNbtAnalyzeCallback.getOrNull()
        delayedDecodeNbtAnalyzeCallback.remove()
        return {
            analyzingResult.set(analyzingResultVal)
            stringifiedArgument.set(stringifiedArgumentVal)
            nbtStringRangeTreeBuilder.set(nbtStringRangeTreeBuilderVal)
            allowMalformed.set(allowMalformedVal)
            furthestAnalyzingResult.set(furthestAnalyzingResultVal)
            delayedDecodeNbtAnalyzeCallback.set(delayedDecodeNbtAnalyzeCallbackVal)
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
        fun get(): TArg
        fun createBranch(): TArg
        fun mergeBranch(argument: TArg, success: Boolean)
    }

    class BranchingArgumentContainer<TArg, TBranch : BranchingArgument<TArg>>(
        val argument: ThreadLocal<TBranch>,
        val argumentWrapper: (TArg) -> TBranch,
    ) {
        fun branchArg(): (successful: Boolean) -> Unit {
            val previousBranch = argument.getOrNull() ?: return {}
            val newBranch = argumentWrapper(previousBranch.createBranch())
            argument.set(newBranch)
            return {
                // Use .get() instead of previous return value of createBranch(), because the value could have changed due to merges since then
                previousBranch.mergeBranch(newBranch.get(), it)
                argument.set(previousBranch)
            }
        }
    }

    data class AnalyzingResultBranchingArgument(var analyzingResult: AnalyzingResult) : BranchingArgument<AnalyzingResult> {
        private var mergedBranchCount = 0
        override fun get() = analyzingResult
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
        override fun get() = stringified
        override fun createBranch() = ArrayList(stringified)
        override fun mergeBranch(argument: MutableList<Either<String, RawResource>>, success: Boolean) {
            if(success)
                stringified = argument.toMutableList()
        }
    }

    data class StringRangeTreeBranchingArgument<TNode: Any>(val stringRangeTreeBuilder: StringRangeTree.PartialBuilder<TNode>) : BranchingArgument<StringRangeTree.PartialBuilder<TNode>> {
        override fun get() = stringRangeTreeBuilder
        override fun createBranch() = stringRangeTreeBuilder.pushBuilder()
        override fun mergeBranch(argument: StringRangeTree.PartialBuilder<TNode>, success: Boolean) {
            if(success)
                stringRangeTreeBuilder.popBuilder(argument)
        }
    }
}