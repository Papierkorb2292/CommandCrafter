package net.papierkorb2292.command_crafter.parser.languages

import com.mojang.brigadier.ParseResults
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContextBuilder
import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import net.minecraft.command.CommandSource
import net.minecraft.command.argument.AngleArgumentType
import net.minecraft.command.argument.BlockMirrorArgumentType
import net.minecraft.command.argument.BlockRotationArgumentType
import net.minecraft.command.argument.ColorArgumentType
import net.minecraft.command.argument.CommandFunctionArgumentType
import net.minecraft.command.argument.DimensionArgumentType
import net.minecraft.command.argument.EntityAnchorArgumentType
import net.minecraft.command.argument.EnumArgumentType
import net.minecraft.command.argument.GameModeArgumentType
import net.minecraft.command.argument.HeightmapArgumentType
import net.minecraft.command.argument.HexColorArgumentType
import net.minecraft.command.argument.IdentifierArgumentType
import net.minecraft.command.argument.ItemSlotArgumentType
import net.minecraft.command.argument.NumberRangeArgumentType
import net.minecraft.command.argument.OperationArgumentType
import net.minecraft.command.argument.RegistryEntryPredicateArgumentType
import net.minecraft.command.argument.RegistryEntryReferenceArgumentType
import net.minecraft.command.argument.RegistryKeyArgumentType
import net.minecraft.command.argument.RegistryPredicateArgumentType
import net.minecraft.command.argument.RegistrySelectorArgumentType
import net.minecraft.command.argument.ScoreboardCriterionArgumentType
import net.minecraft.command.argument.ScoreboardObjectiveArgumentType
import net.minecraft.command.argument.ScoreboardSlotArgumentType
import net.minecraft.command.argument.SlotRangeArgumentType
import net.minecraft.command.argument.SwizzleArgumentType
import net.minecraft.command.argument.TeamArgumentType
import net.minecraft.command.argument.TimeArgumentType
import net.minecraft.command.argument.UuidArgumentType
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.helper.IntList
import net.papierkorb2292.command_crafter.parser.helper.resolveRedirects

/**
 * This class runs parsing attempts for macro analyzing in a certain order such that the analyzer
 * uses the best matching command node to continue after it has encountered an argument which depends on a macro variable.
 *
 * The algorithm for determining the order of parsing attempts might be changed in the future if other algorithms turn out to perform better.
 * Currently, the order of parsing attempts are determined the following way:
 * It is assumed that every argument starts after a space character, so every position of a space character in the remaining macro command
 * is a potential start position. These positions are given through [attemptPositions].
 *
 * Most of the time, a macro variable is just going to cover one argument, in which case the next argument (after the next space character)
 * would be matched by a direct child node of the previous command node, so this should be attempted first.
 * If this fails, there are multiple options for the next attempts:
 *  - The last argument might consist of multiple parts separated by spaces, so maybe the direct child nodes only match a later [attemptPositions] entry
 *  - The macro variable could also resolve to multiple nodes (for example when an entire `execute` subcommand is
 *  passed as a macro variable), in which case it would be good to also match the grand children and such against the first [attemptPositions].
 *  Since this second case probably happens less, it should be given less priority.
 *
 *  To determine the order of parsing attempts, this class has 'crawlers' that go through the [attemptPositions] one by one and try to match each one
 *  with some set of command nodes. This starts by trying to match the first [attemptPositions] entry with the children of [startNode], which means the children of [startNode]
 *  will be used as root nodes by the parser and thus the parser will try to parse the grand children of [startNode] at that position. This is because [startNode] is the
 *  parent of the macro argument, so even though the parser might have recognized the argument with the macro variable as some command node, the crawlers still try out
 *  all other possible command nodes that could have gone there and then try to match their children at the next position. If that fails, the crawler advances
 *  to the next [attemptPositions] entry and tries again. This is repeated until either one of the parsing attempts is good enough or the crawler did five failed
 *  attempts, at which point a second crawler will be spawned back at the beginning of [attemptPositions] which tries to match the children of the previous crawler
 *  (but making sure that it doesn't repeat any command nodes that a previous crawler already tried).
 *  The two (or more) crawlers are then advanced in an alternating manner.
 */
class MacroAnalyzingCrawlerRunner(private val startNode: CommandNode<CommandSource>, private val attemptPositions: IntList, private val baseAnalyzingResult: AnalyzingResult) {

    private val stepsPerCrawlerBeforePush = 5

    private val consumedUnadvanceableNodes = mutableSetOf<CommandNode<CommandSource>>()
    private val consumedAdvanceableNodes = mutableSetOf<CommandNode<CommandSource>>()
    private val crawlers = mutableListOf<Crawler>(createStartCrawler())
    private var finishedCrawlerIndex = 1

    fun runCrawlers(parseCallback: (startCursor: Int, parserRootNode: CommandNode<CommandSource>, analyzingResult: AnalyzingResult) -> CrawlerParserResult): CrawlerParserResult? {
        var bestResult: CrawlerParserResult? = null
        var bestResultAttemptPositionIndex = -1
        val resultsPerAttemptPositionIndex = mutableListOf<MutableList<CrawlerParserResult>>()
        do {
            // Run each crawler for a few steps before adding another crawler
            for(step in 0 until stepsPerCrawlerBeforePush) {
                if(finishedCrawlerIndex == 0)
                    // All current crawlers are finished, so just push a new one
                    break

                for((i, crawler) in crawlers.subList(0, finishedCrawlerIndex).withIndex()) {
                    val attemptIndex = i * stepsPerCrawlerBeforePush + step
                    if(attemptIndex >= attemptPositions.size) {
                        finishedCrawlerIndex = i
                        break // All following crawlers should have already finished anyway
                    }

                    val startCursor = attemptPositions[attemptIndex]
                    val results = crawler.getNodesForAttemptIndex(attemptIndex)
                        .map { node ->
                            parseCallback(startCursor, node, baseAnalyzingResult.copyInput())
                        }.toList()

                    val crawlerBestResult = results.maxOrNull() ?: continue
                    if(bestResult == null || crawlerBestResult > bestResult) {
                        bestResult = crawlerBestResult
                        bestResultAttemptPositionIndex = attemptIndex
                    }

                    while(resultsPerAttemptPositionIndex.size <= attemptIndex) {
                        resultsPerAttemptPositionIndex.add(mutableListOf())
                    }
                    resultsPerAttemptPositionIndex[attemptIndex] += results
                }
                if(bestResult?.shouldStopCrawling() == true) {
                    addCompletionProvidersUpToAttemptPosition(bestResultAttemptPositionIndex, resultsPerAttemptPositionIndex)
                    return buildCombinedCrawlerResult(bestResult, bestResultAttemptPositionIndex)
                }
            }
        } while(pushCrawler())
        if(bestResult == null)
            return null
        addCompletionProvidersUpToAttemptPosition(bestResultAttemptPositionIndex, resultsPerAttemptPositionIndex)
        return buildCombinedCrawlerResult(bestResult, bestResultAttemptPositionIndex)
    }

    private fun addCompletionProvidersUpToAttemptPosition(attemptIndex: Int, resultsPerAttemptPositionIndex: List<List<CrawlerParserResult>>) {
        resultsPerAttemptPositionIndex.asSequence().take(attemptIndex + 1).flatten().forEachIndexed { i, result ->
            baseAnalyzingResult.combineWithCompletionProviders(result.analyzingResult, "_$i")
        }
    }

    private fun buildCombinedCrawlerResult(bestResult: CrawlerParserResult, bestResultAttemptPositionIndex: Int): CrawlerParserResult {
        val attemptPosition = attemptPositions[bestResultAttemptPositionIndex]
        val analyzingResult = baseAnalyzingResult.copy()
        analyzingResult.cutAfterTargetCursor(attemptPosition)
        analyzingResult.combineWithExceptCompletions(bestResult.analyzingResult)
        return bestResult.copy(analyzingResult = analyzingResult)
    }

    /**
     * Adds a new crawler at the beginning that matches the children of the previous children
     * (unless those children have already been covered by another crawler)
     *
     * If there are no remaining children, no new crawler will be added.
     *
     * @return `true` if remaining children were found, otherwise `false`
     */
    private fun pushCrawler(): Boolean {
        val newCrawler = crawlers.first().createChildrenCrawler()
        if(newCrawler.isEmpty())
            return false
        crawlers.add(0, newCrawler)
        finishedCrawlerIndex++
        return true
    }

    private fun createStartCrawler(): Crawler {
        val children = startNode.resolveRedirects().children
        val advanceable = children.filter { canNodeHaveSpaces(it) }.map { it.resolveRedirects() }
        val unadvanceable = children.filter { !canNodeHaveSpaces(it) }.map { it.resolveRedirects() }
        consumedAdvanceableNodes += advanceable
        consumedUnadvanceableNodes += unadvanceable
        return Crawler(advanceable, unadvanceable)
    }

    /**
     * Stores the [advanceableNodes] and [unadvanceableNodes] that are used as the root nodes for the parsing attempt
     *
     * The difference between [advanceableNodes] and [unadvanceableNodes] is that [unadvanceableNodes] are only applied at the very first attempt position,
     * because it is known ahead of time that they won't match any later position well. This is the case for all nodes that are descendants of a node which can not contain any spaces,
     * Because if a child of such a node appears somewhere in the command, then the parent node must also appear before it,
     * meaning it isn't necessary to try and parse the child since that should have already been attempted when parsing the parent at an earlier position.
     * The only exception is when the parent is given through a macro variable, in which case its child would have to be directly after the next space, meaning
     * only the next space actually has to be checked.
     */
    private inner class Crawler(val advanceableNodes: List<CommandNode<CommandSource>>, val unadvanceableNodes: List<CommandNode<CommandSource>>) {
        fun createChildrenCrawler(): Crawler {
            val childAdvanceableNodes = mutableListOf<CommandNode<CommandSource>>()
            val childUnadvanceableNodes = mutableListOf<CommandNode<CommandSource>>()

            // unadvanceable nodes can only add more unadvanceable nodes, since all of their children would
            // have the nodes parent as a grandparent still.
            // Unadvanceable nodes are only added if there isn't already another crawler with that node either
            // as an advanceable node or as an unadvanceable node, since either option would mean all the potential attempts
            // have already been done.
            childUnadvanceableNodes += unadvanceableNodes.flatMap { node ->
                node.children
                    .map { child -> child.resolveRedirects() }
                    .filter {
                        consumedUnadvanceableNodes.add(it) && it !in consumedAdvanceableNodes
                    }
            }
            advanceableNodes.forEach { node ->
                val childNodes = node.children
                    .map { child -> child.resolveRedirects() }
                if(!canNodeHaveSpaces(node))
                    childUnadvanceableNodes += childNodes.filter {
                        consumedUnadvanceableNodes.add(it) && it !in consumedAdvanceableNodes
                    }
                else {
                    childAdvanceableNodes += childNodes.filter(consumedAdvanceableNodes::add)
                }
            }

            return Crawler(childAdvanceableNodes, childUnadvanceableNodes)
        }

        fun getNodesForAttemptIndex(index: Int): Sequence<CommandNode<CommandSource>> =
            if(index == 0) advanceableNodes.asSequence() + unadvanceableNodes.asSequence()
            else advanceableNodes.asSequence()

        fun isEmpty() = advanceableNodes.isEmpty() && unadvanceableNodes.isEmpty()
    }

    data class CrawlerParserResult(val parsedNodeCount: Int, val literalNodeCount: Int, val commandContext: CommandContextBuilder<*>, val analyzingResult: AnalyzingResult): Comparable<CrawlerParserResult> {
        companion object {
            fun fromParseResults(parseResults: ParseResults<*>, analyzingResult: AnalyzingResult): CrawlerParserResult {
                var parsedNodeCount = 0
                var literalNodeCount = 0
                var context: CommandContextBuilder<*>? = parseResults.context
                while(context != null) {
                    parsedNodeCount += context.nodes.size
                    literalNodeCount += context.nodes.count { it.node is LiteralCommandNode }
                    context = context.child
                }

                return CrawlerParserResult(
                    parsedNodeCount,
                    literalNodeCount,
                    parseResults.context,
                    analyzingResult
                )
            }
        }

        fun shouldStopCrawling(): Boolean {
            // If this is the case the node is assumed to match well enough.
            // The exact condition is more or less arbitrary, just see what works well
            return literalNodeCount >= 3
        }

        override fun compareTo(other: CrawlerParserResult): Int =
            if(literalNodeCount != other.literalNodeCount) literalNodeCount.compareTo(other.literalNodeCount)
            else parsedNodeCount.compareTo(other.parsedNodeCount)
    }

    companion object {
        fun canNodeHaveSpaces(node: CommandNode<*>): Boolean {
            if(node is LiteralCommandNode)
                return false
            if(node !is ArgumentCommandNode<*, *>)
                return true
            val argumentType: ArgumentType<*> = node.type
            if(argumentType.javaClass in KNOWN_ARGUMENT_TYPES_WITHOUT_SPACES)
                return false
            if(argumentType is StringArgumentType && argumentType.type == StringArgumentType.StringType.SINGLE_WORD)
                return false
            return true
        }

        val KNOWN_ARGUMENT_TYPES_WITHOUT_SPACES = setOf(
            BoolArgumentType::class.java,
            FloatArgumentType::class.java,
            DoubleArgumentType::class.java,
            IntegerArgumentType::class.java,
            LongArgumentType::class.java,
            ColorArgumentType::class.java,
            HexColorArgumentType::class.java,
            ScoreboardObjectiveArgumentType::class.java,
            ScoreboardCriterionArgumentType::class.java,
            OperationArgumentType::class.java,
            AngleArgumentType::class.java,
            ScoreboardSlotArgumentType::class.java,
            SwizzleArgumentType::class.java,
            TeamArgumentType::class.java,
            ItemSlotArgumentType::class.java,
            SlotRangeArgumentType::class.java,
            IdentifierArgumentType::class.java,
            CommandFunctionArgumentType::class.java,
            EntityAnchorArgumentType::class.java,
            NumberRangeArgumentType.IntRangeArgumentType::class.java,
            NumberRangeArgumentType.FloatRangeArgumentType::class.java,
            DimensionArgumentType::class.java,
            GameModeArgumentType::class.java,
            TimeArgumentType::class.java,
            RegistryEntryPredicateArgumentType::class.java,
            RegistryPredicateArgumentType::class.java,
            RegistryEntryReferenceArgumentType::class.java,
            RegistryKeyArgumentType::class.java,
            RegistrySelectorArgumentType::class.java,
            BlockMirrorArgumentType::class.java,
            BlockRotationArgumentType::class.java,
            HeightmapArgumentType::class.java,
            UuidArgumentType::class.java
        )
    }
}