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
import com.mojang.brigadier.context.ParsedCommandNode
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import com.mojang.brigadier.tree.RootCommandNode
import net.minecraft.command.CommandSource
import net.minecraft.command.argument.AngleArgumentType
import net.minecraft.command.argument.BlockMirrorArgumentType
import net.minecraft.command.argument.BlockRotationArgumentType
import net.minecraft.command.argument.ColorArgumentType
import net.minecraft.command.argument.CommandFunctionArgumentType
import net.minecraft.command.argument.DimensionArgumentType
import net.minecraft.command.argument.EntityAnchorArgumentType
import net.minecraft.command.argument.GameModeArgumentType
import net.minecraft.command.argument.HeightmapArgumentType
import net.minecraft.command.argument.HexColorArgumentType
import net.minecraft.command.argument.IdentifierArgumentType
import net.minecraft.command.argument.ItemSlotArgumentType
import net.minecraft.command.argument.MessageArgumentType
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
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.compareTo
import net.papierkorb2292.command_crafter.helper.IntList
import net.papierkorb2292.command_crafter.helper.binarySearch
import net.papierkorb2292.command_crafter.mixin.editor.processing.macros.CommandContextBuilderAccessor
import net.papierkorb2292.command_crafter.mixin.editor.processing.macros.CommandDispatcherAccessor
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.helper.resolveRedirects
import kotlin.collections.plusAssign
import kotlin.math.max

/**
 *
 * The algorithm for determining the order of parsing attempts might be changed in the future if other algorithms turn out to perform better.
 * Currently, analyzing macros works the following way:
 *
 * When a macro variable is encountered by the normal parser, the parser will stop there. Afterward, all the nodes that have been read before that are
 * analyzed. Additionally, the analyzer is used to read the node that contains the macro with more leniency. The next node is then assumed to start at one
 * of the whitespaces after that (in most cases it will start directly at the next whitespace, but there might be cases where the node with the macro contains whitespaces
 * itself but the lenient parser failed to read them in). To go through all following whitespaces and attempt to start parsing there, a `Crawler` is created, which tries
 * out all possible nodes that the macro could resolve to and then start parsing with them at each of the following whitespaces. Since some macro variables might also
 * resolve to multiple nodes, it might not work to try parsing any direct child of the parent node. Thus, a [Spawner] is created when a macro is encountered, which can
 * create multiple [Spawner.Crawler]s (one for the direct children, one for the grand-children, and so on). The next crawler in the sequence is only created after the previous
 * one already had a few unsuccessful attempts.
 *
 * To try to find the best matching nodes as quickly as possible, the algorithm doesn't use a straightforward BSF or DSF (the former would quickly slow done with many macros
 * and the latter has a chance to hang on incorrect attempts for a long time). Instead, all spawners are weighted depending on how far they've parsed (positive) and how many
 * attempts that spawner has already made (negative). The best spawners according to this metric are chosen to make another attempt. Additionally, if the result of an attempt
 * was successful enough, some previous spawners can be removed (see [CrawlerResult.cutSpawnerTree])
 *
 * @param reader The [DirectiveStringReader] containing the macro command contents where all macro variables have been resolved as an empty string
 * @param variableLocations A list of all cursor positions in the input where a macro variable was.
 */
class MacroAnalyzingCrawlerRunner(
    private val baseContext: CommandContextBuilder<CommandSource>,
    private val reader: DirectiveStringReader<AnalyzingResourceCreator>,
    private val variableLocations: IntList,
    private val baseAnalyzingResult: AnalyzingResult
) {
    private val attemptPositions = IntList()
    init {
        attemptPositions.add(0)
        for(i in 0 until reader.string.length)
            if(reader.string[i] == ' ' && i + 1 < reader.string.length)
                attemptPositions.add(i + 1)
    }
    private val invalidAttemptPositionsMarker = BooleanArray(attemptPositions.size)

    private val weightedSpawners = mutableListOf(mutableListOf(createRootSpawner()))

    private var mergedCompletionsCount = 0

    fun run(): AnalyzingResult {
        var bestGlobalSpawner: Spawner? = null
        while(weightedSpawners.isNotEmpty()) {
            val mostPromisingSpawners = weightedSpawners.removeLast()
            if(mostPromisingSpawners.isEmpty())
                continue
            val spawnersIndex = weightedSpawners.size

            mostPromisingSpawners.forEach {
                if(Thread.currentThread().isInterrupted)
                    return baseAnalyzingResult
                it.runCrawlersOnce(spawnersIndex)
                it.bestResult?.markInvalidAttemptPositions()
            }
            val bestMatchingSpawner = mostPromisingSpawners.asSequence()
                .filter { it.bestResult != null }
                .maxByOrNull { it.bestResult!! }
                ?: continue

            if(bestGlobalSpawner == null || bestMatchingSpawner.bestResult!! > bestGlobalSpawner.bestResult!!) {
                bestGlobalSpawner = bestMatchingSpawner
            }

            val advancedSpawners = mostPromisingSpawners.filterTo(mutableListOf()) { it.advance() }
            if(advancedSpawners.isNotEmpty()) {
                // Advancing the spawners decreases their weight by one
                if(spawnersIndex > 0)
                    weightedSpawners[spawnersIndex - 1] += advancedSpawners
                else
                    weightedSpawners.add(0, advancedSpawners)
            }

            bestMatchingSpawner.bestResult!!.cutSpawnerTree()
        }
        return bestGlobalSpawner!!.buildCombinedAnalyzingResult(reader.string.length)
    }

    private fun createRootSpawner() = Spawner(null, listOf(), 0, baseContext).apply {
        addRootCrawler(baseContext.dispatcher.root)
    }

    private fun getAttemptIndexForCursor(cursor: Int): Int {
        val index = attemptPositions.binarySearch { attemptPositions[it].compareTo(cursor) }
        return if(index >= 0)
            index
        else
            -(index + 1) // Get the next attempt position
    }

    private fun tryParse(
        rootNode: CommandNode<CommandSource>,
        spawner: Spawner,
        spawnerIndex: Int
    ): CrawlerResult {
        val analyzingResult = baseAnalyzingResult.copyInput()
        val startCursor = reader.cursor
        val originalString = reader.string

        // Only let the parser read up to the next variable location, because what comes after that doesn't matter in this call anyway, it will only be parsed later
        // (either when analyzing the last node of this segment or when trying to parse nodes in other segments)
        var nextVariableLocationIndex = variableLocations.binarySearch {
            variableLocations[it].compareTo(startCursor)
        }
        if(nextVariableLocationIndex < 0) {
            nextVariableLocationIndex = -(nextVariableLocationIndex + 1)
        }
        val nextVariableLocation = if(nextVariableLocationIndex >= variableLocations.size) originalString.length else variableLocations[nextVariableLocationIndex]
        reader.setString(originalString.substring(0, nextVariableLocation))

        // The parent node isn't in the context yet, since it can vary for each attempt, but the parser expects it to be there
        // (for example analyzeParsedCommand will use the last node in the context to determine the candidates for tryAnalyzeNextNode)
        val attemptBaseContext = spawner.baseContext.copy()
        if(spawner.parent != null)
            // Uses a string range of length 0, because only the end is really important and having a non-zero length could
            // cause issues because the node is given to markInvalidAttemptPositions but might contain valid attempt positions
            attemptBaseContext.withNode(rootNode, StringRange.at(startCursor - 1)) // Subtracts one to exclude space

        val commandParseResults: ParseResults<CommandSource>
        @Suppress("UNCHECKED_CAST")
        commandParseResults = (baseContext.dispatcher as CommandDispatcherAccessor<CommandSource>).callParseNodes(
            rootNode,
            reader,
            attemptBaseContext
        )
        reader.copyFrom(commandParseResults.reader as DirectiveStringReader<*>)
        reader.setString(originalString)

        // Remove the nodes that contain the macro variable, because the macro variable is supposed to be read with
        // all available leniency by analyzing the node
        if(nextVariableLocationIndex < variableLocations.size)
            removeNodesAfterCursor(commandParseResults, nextVariableLocation)
        reader.cursor = getLastCursor(commandParseResults.context)
        if(reader.canRead())
            reader.skip() // Also skip spaces

        // This can also skip more characters when trying to analyze the next command node
        macroLanguage.analyzeParsedCommand(
            commandParseResults,
            analyzingResult,
            reader,
            attemptBaseContext.nodes.size
        )

        // In case the analyzer didn't read what the actual parser read, use the end cursor of the parser instead,
        // because that should still be part of the argument
        reader.cursor = max(reader.cursor, commandParseResults.reader.cursor)

        val hasAccessedMacro = nextVariableLocationIndex < variableLocations.size && reader.furthestAccessedCursor >= nextVariableLocation

        if(!hasAccessedMacro || !reader.canRead())
            // Don't parse further, because there was either an error before a macro was encountered (these errors are not handled gracefully, just like when analyzing normal commands)
            // or because the command is done
            return convertParseResultsToCrawlerResult(commandParseResults, attemptBaseContext, analyzingResult, spawner, null)

        if(reader.furthestAccessedCursor <= startCursor + 1 && spawner.parent != null)
            // The parser doesn't seem to have found anything, there's no need to create a new spawner, since the parent spawner is also going to try
            // the following whitespaces
            return convertParseResultsToCrawlerResult(commandParseResults, attemptBaseContext, analyzingResult, spawner, null)

        // The last argument had a macro variable so it might not be correct to continue with the next node like normal,
        // since the macro could have contained any data whatsoever. Create a new spawner to find the best match to continue parsing.
        val lastChild = commandParseResults.context.lastChild
        val lastNode = lastChild.nodes.lastOrNull()?.node ?: lastChild.rootNode

        if(reader.canRead() && reader.cursor <= nextVariableLocation)
            // Make sure that the variable is skipped, such that the next spawner only starts after the variable even if the variable has a space before it.
            // Only triggers if tryAnalyzeNextNode didn't already skip characters after the variable
            reader.cursor = nextVariableLocation + 1

        val nextAttemptIndex = getAttemptIndexForCursor(reader.cursor)
        if(nextAttemptIndex >= attemptPositions.size || invalidAttemptPositionsMarker[nextAttemptIndex])
            return convertParseResultsToCrawlerResult(commandParseResults, attemptBaseContext, analyzingResult, spawner, null)
        val childSpawner = Spawner(spawner, lastNode.resolveRedirects().children.toList(), nextAttemptIndex, commandParseResults.context.lastChild)
        if(childSpawner.nextNodes.isEmpty())
            return convertParseResultsToCrawlerResult(commandParseResults, attemptBaseContext, analyzingResult, spawner, null)
        childSpawner.pushCrawler()

        // Use the difference in the attempt index from the parent spawner to add the child spawner instead of just using nextAttemptIndex
        // as index for weightedSpawners, because the parent spawner might have already been moved back some steps, which should also apply
        // to its children
        val attemptIndexDiff = nextAttemptIndex - spawner.startAttemptIndex
        val childSpawnerIndex = spawnerIndex + attemptIndexDiff
        while(weightedSpawners.size <= childSpawnerIndex) {
            weightedSpawners += mutableListOf<Spawner>()
        }
        weightedSpawners[childSpawnerIndex] += childSpawner

        val crawlerResult = convertParseResultsToCrawlerResult(commandParseResults, attemptBaseContext, analyzingResult, spawner, childSpawner)
        childSpawner.baseResult = crawlerResult
        return crawlerResult
    }

    private fun <TNode> removeNodesAfterCursor(parseResults: ParseResults<TNode>, endCursor: Int) {
        var contextBuilder: CommandContextBuilder<TNode>? = parseResults.context
        while(contextBuilder != null) {
            for((i, node) in contextBuilder.nodes.withIndex()) {
                val shouldRemove = endCursor <= node.range.end
                if(shouldRemove) {
                    val prevNode = contextBuilder.nodes.getOrNull(i - 1) ?: ParsedCommandNode(
                        contextBuilder.rootNode,
                        StringRange.at(contextBuilder.range.start)
                    )
                    val childNodes = contextBuilder.nodes.subList(i, contextBuilder.nodes.size)
                    for(childNode in childNodes) {
                        parseResults.exceptions.remove(childNode.node)
                        if(childNode.node is ArgumentCommandNode<*, *>)
                            contextBuilder.arguments.remove(childNode.node.name)
                    }
                    childNodes.clear()

                    @Suppress("UNCHECKED_CAST")
                    val contextBuilderAccessor =
                        contextBuilder as CommandContextBuilderAccessor<TNode>
                    contextBuilderAccessor.setForks(prevNode.node.isFork)
                    contextBuilderAccessor.setModifier(prevNode.node.redirectModifier)
                    contextBuilderAccessor.setRange(
                        StringRange(
                            contextBuilder.range.start,
                            prevNode.range.end
                        )
                    )
                    return
                }
            }
            contextBuilder = contextBuilder.child
        }
    }

    private fun getLastCursor(commandContextBuilder: CommandContextBuilder<*>): Int {
        // Go through all contexts instead of just using lastChild.range, because the last child might not
        // have any nodes if it's after a redirect, but its cursor will already have skipped the whitespace,
        // thereby causing inconsistent behaviour
        var context: CommandContextBuilder<*>? = commandContextBuilder
        var cursor = 0
        while(context != null) {
            if(context.nodes.isNotEmpty())
                cursor = context.range.end
            context = context.child
        }
        return cursor
    }

    private inner class Spawner(
        val parent: Spawner?,
        var nextNodes: List<CommandNode<CommandSource>>,
        val startAttemptIndex: Int,
        val baseContext: CommandContextBuilder<CommandSource>
    ) {
        val consumedCrawlerNodes = mutableSetOf<CommandNode<CommandSource>>()
        val accessedChildNodes = mutableSetOf<CommandNode<CommandSource>>()
        val crawlers = mutableListOf<Crawler>()

        /**
         * Stores all analyzing results from a crawler attempt at `analyzingResultsPerAttemptCount[attemptCount][skippedNodeCount]`
         */
        val attemptAnalyzingResults = mutableListOf<MutableList<List<AnalyzingResult>?>>()
        var baseResult: CrawlerResult? = null
        var skippedNodeCount: Int = 0

        var bestResult: CrawlerResult? = null
        var bestResultAttemptCount: Int = -1
        var bestResultSkippedNodeCount: Int = -1

        fun advance(): Boolean {
            if(isStartInvalid())
                return false
            if(crawlers.isEmpty() || crawlers.last().attemptCount >= STEPS_PER_CRAWLER_BEFORE_PUSH)
                pushCrawler()
            return crawlers.isNotEmpty()
        }

        fun runCrawlersOnce(spawnerIndex: Int) {
            if(isStartInvalid())
                return
            var i = 0
            while(i < crawlers.size) {
                val crawler = crawlers[i]
                val crawlerNodes = crawler.getNodesForAttempt()

                val attemptCount = crawler.attemptCount++
                while(attemptAnalyzingResults.size <= attemptCount) {
                    attemptAnalyzingResults.add(mutableListOf())
                }
                val attemptAnalyzingResultsForAttemptCount = attemptAnalyzingResults[attemptCount]
                while(attemptAnalyzingResultsForAttemptCount.size <= crawler.skippedNodeCount) {
                    attemptAnalyzingResultsForAttemptCount.add(null)
                }

                val attemptIndex = startAttemptIndex + attemptCount
                if(invalidAttemptPositionsMarker[attemptIndex]) {
                    if(attemptIndex + 1 >= attemptPositions.size || crawler.nodesWithSpaces.isEmpty())
                        crawlers.removeAt(i)
                    else
                        i++

                    continue
                }
                val startCursor = attemptPositions[attemptIndex]

                val results = crawlerNodes.map { node ->
                        reader.cursor = startCursor
                        reader.furthestAccessedCursor = 0
                        tryParse(node, this, spawnerIndex)
                    }

                attemptAnalyzingResultsForAttemptCount[crawler.skippedNodeCount] = results.map { it.analyzingResult }

                if(attemptIndex + 1 >= attemptPositions.size || crawler.nodesWithSpaces.isEmpty())
                    crawlers.removeAt(i)
                else
                    i++

                val crawlerBestResult = results.maxOrNull() ?: continue
                if(bestResult == null || crawlerBestResult > bestResult!!) {
                    bestResult = crawlerBestResult
                    bestResultAttemptCount = attemptCount
                    bestResultSkippedNodeCount = crawler.skippedNodeCount
                }
            }
        }

        fun pushCrawler() {
            if(nextNodes.isEmpty())
                return
            val redirectedNodes = nextNodes.map { it.resolveRedirects() }
            crawlers += Crawler(
                redirectedNodes,
                nextNodes.mapIndexedNotNull { i, node ->
                    if(canNodeHaveSpaces(node) && consumedCrawlerNodes.add(redirectedNodes[i]))
                        redirectedNodes[i]
                    else null
                },
                skippedNodeCount++
            )
            nextNodes = redirectedNodes.flatMap { it.children }.filter(accessedChildNodes::add)
        }

        // Root node for the start of the command is added separately, because it is known that this node
        // must match the first position, so it's unnecessary to check its children or to try to match it on a later attempt position
        fun addRootCrawler(node: RootCommandNode<CommandSource>) {
            crawlers.add(Crawler(listOf(node), listOf()))
        }

        fun buildCombinedAnalyzingResult(cutTargetCursor: Int): AnalyzingResult {
            val bestResult = bestResult!!
            val attemptPosition = attemptPositions[startAttemptIndex + bestResultAttemptCount]
            val parentAnalyzingResult = parent?.buildCombinedAnalyzingResult(attemptPosition)
                ?: baseAnalyzingResult.copy().also {
                    it.cutAfterTargetCursor(attemptPosition + reader.readSkippingChars)
                }
            val crawlerAnalyzingResult = baseAnalyzingResult.copyInput()
            crawlerAnalyzingResult.combineWithExceptCompletions(bestResult.analyzingResult)
            addCompletionProvidersUpToAttemptPosition(crawlerAnalyzingResult)
            crawlerAnalyzingResult.cutAfterTargetCursor(cutTargetCursor + reader.readSkippingChars)
            parentAnalyzingResult.combineWith(crawlerAnalyzingResult)
            return parentAnalyzingResult
        }

        private fun isStartInvalid(): Boolean = invalidAttemptPositionsMarker[startAttemptIndex]

        /**
         * Adds all completions from parsing attempts with an attemptCount less than or equal
         * to the best result and a skippedNodeCount less than or equal to the best result.
         * Other completions are deemed not necessary and maybe confusing.
         */
        private fun addCompletionProvidersUpToAttemptPosition(analyzingResult: AnalyzingResult) {
            attemptAnalyzingResults.asSequence()
                .take(bestResultAttemptCount + 1)
                .flatMap { it.asSequence().take(bestResultSkippedNodeCount + 1) }
                .filterNotNull()
                .flatten()
                .forEach { result ->
                    analyzingResult.combineWithCompletionProviders(result, "_${mergedCompletionsCount++}")
                }
        }

        private inner class Crawler(val nodes: List<CommandNode<CommandSource>>, val nodesWithSpaces: List<CommandNode<CommandSource>>, val skippedNodeCount: Int = 0, var attemptCount: Int = 0) {
            // For any attemptCount > 0 only parent nodes that contain spaces are tried. This is because if the parent
            // node can not contain spaces, then it's either contained by the macro variable, in which case its children
            // would appear at the first attempt position after the macro only, or the parent node could also be part of
            // the input, in which case it's unnecessary to try and parse its children, because a previous crawler would
            // have already foud the parent node.
            fun getNodesForAttempt() = if(attemptCount == 0) nodes else nodesWithSpaces
        }
    }

    private inner class CrawlerResult(val parsedNodeCount: Int, val literalNodeCount: Int, val semanticTokensCount: Int, val contextBuilder: CommandContextBuilder<CommandSource>, val analyzingResult: AnalyzingResult, val parentSpawner: Spawner, val childSpawner: Spawner?): Comparable<CrawlerResult> {
        /**
         * Rate how successful this parsing attempt was and based on that remove any previous spawner that don't seem useful enough anymore.
         *
         * With the current algorithm, that means that if more than three literals have been parsed after a certain point, then
         * an any spawners before that point are removed, effectively "locking them in". This is done because parsing three literals
         * seems like a good indication that the parser is on the right path.
         */
        fun cutSpawnerTree() {
            // Exact conditions for where to cut off are more or less arbitrary, just see what works well
            val minLiteralCountForConfidence = 3
            if(literalNodeCount < minLiteralCountForConfidence)
                return

            if(parentSpawner.baseResult == null && literalNodeCount > minLiteralCountForConfidence) {
                weightedSpawners.clear()
                if(childSpawner != null)
                    weightedSpawners += mutableListOf(childSpawner)
                return
            }
            if(parentSpawner.baseResult != null && literalNodeCount - parentSpawner.baseResult!!.literalNodeCount >= minLiteralCountForConfidence) {
                weightedSpawners.clear()
                if(childSpawner != null)
                    weightedSpawners += mutableListOf(childSpawner)
                return
            }

            val lockedInLiterals = literalNodeCount - minLiteralCountForConfidence
            var cutOffPoint: Spawner? = null
            var root = parentSpawner
            while((root.baseResult ?: return).literalNodeCount > lockedInLiterals) {
                cutOffPoint = root
                root = root.parent!!
            }
            // Loop ran at least once, because the case where it wouldn't was already covered by the `if` before it
            check(cutOffPoint != null)

            val cutOffIndex = weightedSpawners.indexOfFirst { it.contains(cutOffPoint) }
            if(cutOffIndex == -1)
                // The tree was already cut at or after this spawner
                return
            weightedSpawners.subList(0, cutOffIndex).clear()
            weightedSpawners[0] = mutableListOf(cutOffPoint)
            val addedNodes = mutableSetOf(cutOffPoint)

            for(spawners in weightedSpawners.subList(1, weightedSpawners.size)) {
                spawners.removeAll {
                    it.parent !in addedNodes
                }
                addedNodes += spawners
            }
        }

        fun markInvalidAttemptPositions() {
            var attemptIndex = 0
            for(parsedNode in contextBuilder.nodes) {
                if(attemptIndex >= attemptPositions.size)
                    return
                if(!canNodeHaveSpaces(parsedNode.node) || isGreedyString(parsedNode.node))
                    continue
                while(attemptIndex < attemptPositions.size && attemptPositions[attemptIndex] <= parsedNode.range.start)
                    attemptIndex++
                while(attemptIndex < attemptPositions.size && attemptPositions[attemptIndex] <= parsedNode.range.end) {
                    invalidAttemptPositionsMarker[attemptIndex] = true
                    attemptIndex++
                }
            }
            // Also set invalidAttemptPositionMarkers for the last that contains macros, which we know ends where the next spawner starts
            val childSpawner = childSpawner ?: return
            while(attemptIndex < attemptPositions.size && attemptPositions[attemptIndex] <= contextBuilder.range.end + 1)
                attemptIndex++
            while(attemptIndex < childSpawner.startAttemptIndex) {
                invalidAttemptPositionsMarker[attemptIndex] = true
                attemptIndex++
            }
        }

        override fun compareTo(other: CrawlerResult): Int =
            if(literalNodeCount != other.literalNodeCount) literalNodeCount.compareTo(other.literalNodeCount)
            else semanticTokensCount.compareTo(other.semanticTokensCount)
    }

    private fun convertParseResultsToCrawlerResult(parseResults: ParseResults<CommandSource>, baseContext: CommandContextBuilder<CommandSource>, analyzingResult: AnalyzingResult, parentSpawner: Spawner, childSpawner: Spawner?): CrawlerResult {
        var previouslyCountedNodes = baseContext.nodes.size

        var parsedNodeCount = parentSpawner.baseResult?.parsedNodeCount ?: 0
        var literalNodeCount = parentSpawner.baseResult?.literalNodeCount ?: 0
        var semanticTokensCount = parentSpawner.baseResult?.semanticTokensCount ?: 0
        var context: CommandContextBuilder<*>? = parseResults.context
        while(context != null) {
            parsedNodeCount += context.nodes.size - previouslyCountedNodes
            literalNodeCount += context.nodes.subList(previouslyCountedNodes, context.nodes.size)
                .count { it.node is LiteralCommandNode }
            context = context.child
            previouslyCountedNodes = 0
        }
        semanticTokensCount += analyzingResult.semanticTokens.size
        return CrawlerResult(
            parsedNodeCount,
            literalNodeCount,
            semanticTokensCount,
            parseResults.context,
            analyzingResult,
            parentSpawner,
            childSpawner
        )
    }

    companion object {
        private const val STEPS_PER_CRAWLER_BEFORE_PUSH = 5
        private val macroLanguage = VanillaLanguage()

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

        fun isGreedyString(node: CommandNode<*>): Boolean {
            if(node !is ArgumentCommandNode<*, *>)
                return false
            val argumentType = node.type
            if(argumentType is StringArgumentType && argumentType.type == StringArgumentType.StringType.GREEDY_PHRASE)
                return true
            if(argumentType is MessageArgumentType)
                return true
            return false
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