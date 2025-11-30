package net.papierkorb2292.command_crafter.parser.languages

import com.mojang.brigadier.CommandDispatcher
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
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import com.mojang.brigadier.tree.RootCommandNode
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.command.CommandSource
import net.minecraft.command.argument.AngleArgumentType
import net.minecraft.command.argument.ArgumentTypes
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
import net.minecraft.command.argument.serialize.ArgumentSerializer
import net.minecraft.network.PacketByteBuf
import net.minecraft.registry.Registries
import net.minecraft.util.Util
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.helper.IntList
import net.papierkorb2292.command_crafter.helper.binarySearch
import net.papierkorb2292.command_crafter.mixin.editor.processing.macros.CommandContextBuilderAccessor
import net.papierkorb2292.command_crafter.mixin.editor.processing.macros.CommandDispatcherAccessor
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.helper.resolveRedirects
import java.util.WeakHashMap
import java.util.concurrent.TimeUnit
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
 * and the latter has a chance to hang on incorrect attempts for a long time). Instead, all spawners are weighted depending on how far they've parsed (+1) and how many
 * attempts that spawner has already made (-1). The best spawners according to this metric are chosen to make another attempt. Additionally, if the result of an attempt
 * was successful enough, some previous spawners can be removed (see [CrawlerResult.cutSpawnerTree])
 *
 * Furthermore, the algorithm saves some additional time by assuming that any whitespace that is skipped by an argument parser (for example in positions or in SNBT) can't
 * be the start of a new node. Thus, these whitespaces are marked as invalid attempt positions and skipped in future attempts. Additionally, the algorithm remembers which
 * nodes have already been attempted at a position and failed or parsed all the way to the end of the input, such that no children can be parsed anymore. These nodes
 * can be skipped in future attempts using [UnnecessaryAttemptDeduplicator].
 *
 * Also, there's a special handling for completions generated by 'trailing' spawners (= all spawners which didn't parse any new node and no spawner after them parsed any new node either).
 * Completions for those spawners will not be limited to the best attempt like with other spawners, instead all completions from all attempts will be added, because even the best result
 * is probably not a good indication of what the user might want to type next in this case.
 *
 * @param reader The [DirectiveStringReader] containing the macro command contents where all macro variables have been resolved as an empty string
 * @param variableLocations A list of all cursor positions in the input where a macro variable was.
 */
@OptIn(ExperimentalUnsignedTypes::class)
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
            if(reader.string[i] == ' ')
                attemptPositions.add(i + 1)
    }
    private val invalidAttemptPositionsMarker = BooleanArray(attemptPositions.size)
    private val attemptPositionsFollowingVariable = BooleanArray(attemptPositions.size)
    init {
        var variableIndex = 0
        var attemptIndex = 0
        while(variableIndex < variableLocations.size && attemptIndex < attemptPositions.size) {
            if(attemptPositions[attemptIndex] > variableLocations[variableIndex]) {
                attemptPositionsFollowingVariable[attemptIndex] = true
                variableIndex++
            }
            attemptIndex++
        }
    }
    private val unnecessaryAttemptDeduplicator = UnnecessaryAttemptDeduplicator()

    private val weightedSpawners = mutableListOf(mutableListOf(createRootSpawner()))

    private var mergedCompletionsCount = 0

    private var timeoutStartNs = -1L
    var hasHitTimeout: Boolean = false
        private set

    fun run(): AnalyzingResult {
        timeoutStartNs = Util.getMeasuringTimeNano()

        var bestGlobalSpawner: Spawner? = null
        val trailingSpawners = LinkedHashSet<Spawner>()
        while(weightedSpawners.isNotEmpty()) {
            if(checkDidHitTimeout()) break

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
            val hasNewNodes = bestMatchingSpawner.bestResult!!.hasNewLiteralNodes()
            if(hasNewNodes)
                trailingSpawners.clear()

            val advancedSpawners = mutableListOf<Spawner>()
            for(spawner in mostPromisingSpawners) {
                if(spawner.advance()) {
                    advancedSpawners += spawner
                    continue
                }
                if(!hasNewNodes)
                    trailingSpawners += spawner
            }
            if(advancedSpawners.isNotEmpty()) {
                // Advancing the spawners decreases their weight by one
                if(spawnersIndex > 0)
                    weightedSpawners[spawnersIndex - 1] += advancedSpawners
                else
                    weightedSpawners.add(0, advancedSpawners)
            }

            bestMatchingSpawner.bestResult!!.cutSpawnerTree()
        }
        isAnalyzingMacroForFirstTime = false
        // Just to be sure, because bestGlobalSpawner is supposed to be set by the loop unless it hits the timeout at the start of the first iteration
        if(hasHitTimeout && bestGlobalSpawner == null)
            return baseAnalyzingResult.copyInput()

        trailingSpawners += bestGlobalSpawner!!
        val result = bestGlobalSpawner.buildCombinedAnalyzingResult(isChildTrailing = true, isLastChild = true)
        val consumedCompletions = createNonTrailingSpawnerHierarchySet(bestGlobalSpawner)
        while(trailingSpawners.isNotEmpty()) {
            val completionSpawner = trailingSpawners.removeFirst()
            if(completionSpawner in consumedCompletions)
                continue
            completionSpawner.addAllCompletionProviders(result)
            consumedCompletions += completionSpawner
            if(completionSpawner.parent != null)
                trailingSpawners += completionSpawner.parent
        }
        return result
    }

    private fun checkDidHitTimeout(): Boolean {
        if(!shouldCheckForTimeout)
            return false
        // Increase timeout duration when running macro analyzing for the first time to avoid false positives when the running program isn't completely optimized yet
        val adjustedTimeoutDurationNs = if(isAnalyzingMacroForFirstTime) timeoutDurationNs * 10 else timeoutDurationNs
        hasHitTimeout = Util.getMeasuringTimeNano() - adjustedTimeoutDurationNs >= timeoutStartNs
        return hasHitTimeout
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
        spawnerIndex: Int,
        attemptIndex: Int
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

        val nextNodeStartCursor = reader.cursor
        // This can also skip more characters when trying to analyze the next command node
        val analyzingFootprint = macroLanguage.analyzeParsedCommand(
            commandParseResults,
            analyzingResult,
            reader,
            attemptBaseContext.nodes.size
        )
        // Mark any attempt indices skipped by tryAnalyzeNextNode invalid. This is important when encountering arguments like SNBT with macros,
        // because the macros likely lead the parser to fail but the lenient parser will skip them.
        if(analyzingFootprint.triedNextNode != null && !isGreedyString(analyzingFootprint.triedNextNode)) {
            var skippedAttemptIndex = attemptIndex
            while(skippedAttemptIndex < attemptPositions.size && attemptPositions[skippedAttemptIndex] <= nextNodeStartCursor)
                skippedAttemptIndex++
            while(skippedAttemptIndex < attemptPositions.size && attemptPositions[skippedAttemptIndex] < reader.cursor) { // Only < and not <=, because some lenient parser consume the next whitespace (for example positions)
                invalidAttemptPositionsMarker[skippedAttemptIndex] = true
                skippedAttemptIndex++
            }
        }

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
        var cursor = -1 // Start at negative one to simulate a space after the root node
        while(context != null) {
            if(context.nodes.isNotEmpty())
                cursor = context.range.end
            context = context.child
        }
        return cursor
    }

    private fun createNonTrailingSpawnerHierarchySet(endSpawner: Spawner): MutableSet<Spawner> {
        val result = mutableSetOf<Spawner>()
        var isTrailing = true
        var spawner: Spawner? = endSpawner
        while(spawner != null) {
            isTrailing = isTrailing && !spawner.bestResult!!.hasNewLiteralNodes()
            if(!isTrailing)
                result += spawner
            spawner = spawner.parent
        }
        return result
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
         * Stores all analyzing results from a crawler attempt at `attemptAnalyzingResults[attemptCount][skippedNodeCount]`
         */
        val attemptAnalyzingResults = mutableListOf<MutableList<List<AnalyzingResult>?>>()
        var baseResult: CrawlerResult? = null
        var skippedNodeCount: Int = 0
        var maxAttemptCountChecked: Int = 0

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
                val attemptIndex = startAttemptIndex + attemptCount

                // Push another crawler if a variable is at this position, so if this variable resolves to a whole node
                // the correct parser for the children will be found quicker
                if(attemptCount > maxAttemptCountChecked) {
                    maxAttemptCountChecked = attemptCount
                    if(attemptPositionsFollowingVariable[attemptIndex])
                        pushCrawler()
                }

                if(crawlerNodes.isEmpty() || invalidAttemptPositionsMarker[attemptIndex]) {
                    // Just skip to the next one
                    if(attemptIndex + 1 >= attemptPositions.size)
                        crawlers.removeAt(i)
                    else
                        i++
                    continue
                }
                while(attemptAnalyzingResults.size <= attemptCount) {
                    attemptAnalyzingResults.add(mutableListOf())
                }
                val attemptAnalyzingResultsForAttemptCount = attemptAnalyzingResults[attemptCount]
                while(attemptAnalyzingResultsForAttemptCount.size <= crawler.skippedNodeCount) {
                    attemptAnalyzingResultsForAttemptCount.add(null)
                }

                val startCursor = attemptPositions[attemptIndex]

                val results = crawlerNodes.mapNotNull { node ->
                        if(unnecessaryAttemptDeduplicator.shouldSkipAttempt(attemptIndex, node))
                            return@mapNotNull null
                        reader.cursor = startCursor
                        reader.furthestAccessedCursor = 0
                        val result = tryParse(node, this, spawnerIndex, attemptIndex)
                        if(result.newNodeCount() == 0)
                            unnecessaryAttemptDeduplicator.markUnnecessaryAttempt(attemptIndex, node)
                        else if(!reader.canRead() && result.newNodeCount() == 1) {
                            var lastNode: ParsedCommandNode<CommandSource>? = null
                            var context: CommandContextBuilder<CommandSource>? = result.contextBuilder
                            while(context != null) {
                                lastNode = context.nodes.lastOrNull() ?: lastNode
                                context = context.child
                            }
                            unnecessaryAttemptDeduplicator.markTrailingNode(attemptIndex, lastNode!!.node)
                        }
                        result
                    }

                attemptAnalyzingResultsForAttemptCount[crawler.skippedNodeCount] = results.map { it.analyzingResult }

                if(attemptIndex + 1 >= attemptPositions.size)
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

        fun buildCombinedAnalyzingResult(isChildTrailing: Boolean, isLastChild: Boolean): AnalyzingResult {
            val cutTargetCursor = if(isLastChild) reader.string.length else attemptPositions[startAttemptIndex]
            val result =
                if(isLastChild) bestResult!!
                else baseResult ?: return baseAnalyzingResult.copyInput()
            val isTrailing = isChildTrailing && !result.hasNewLiteralNodes()
            // If `isLastChild`, the next spawner is `this` again, because the current call is processing this.bestResult so this.baseResult should be processed next
            val nextSpawner = if(isLastChild) this else parent!!
            val parentAnalyzingResult = nextSpawner.buildCombinedAnalyzingResult(isTrailing, false)
            val crawlerAnalyzingResult = baseAnalyzingResult.copyInput()
            crawlerAnalyzingResult.combineWithExceptCompletions(result.analyzingResult)
            // Don't add completion providers for trailing nodes, because those should be added by `addAllCompletionsProviders` (called in `run`) and they shouldn't be added twice
            if(!isTrailing)
                nextSpawner.addCompletionProvidersUpToAttemptPosition(crawlerAnalyzingResult)
            crawlerAnalyzingResult.cutAfterTargetCursor(cutTargetCursor)
            parentAnalyzingResult.combineWith(crawlerAnalyzingResult)
            return parentAnalyzingResult
        }

        private fun isStartInvalid(): Boolean = invalidAttemptPositionsMarker[startAttemptIndex]

        /**
         * Adds all completions from parsing attempts with an attemptCount less than or equal
         * to the best result and a skippedNodeCount less than or equal to the best result.
         * Other completions are deemed not necessary and maybe confusing.
         */
        fun addCompletionProvidersUpToAttemptPosition(analyzingResult: AnalyzingResult) {
            attemptAnalyzingResults.asSequence()
                .take(bestResultAttemptCount + 1)
                .flatMap { it.asSequence().take(bestResultSkippedNodeCount + 1) }
                .filterNotNull()
                .flatten()
                .forEach { result ->
                    analyzingResult.combineWithCompletionProviders(result, "_${mergedCompletionsCount++}")
                }
        }

        fun addAllCompletionProviders(analyzingResult: AnalyzingResult) {
            attemptAnalyzingResults.asSequence()
                .flatten()
                .filterNotNull()
                .flatten()
                .forEach { result ->
                    analyzingResult.combineWithCompletionProviders(result, "_${mergedCompletionsCount++}")
                }
        }

        private inner class Crawler(val nodes: List<CommandNode<CommandSource>>, val nodesWithSpaces: List<CommandNode<CommandSource>>, val skippedNodeCount: Int = 0, var attemptCount: Int = 0) {
            // For any attempt not following a variable only parent nodes that contain spaces are tried. This is because if the parent
            // node can not contain spaces, then it's either contained by the macro variable, in which case its children
            // would appear at the first attempt position after the variable only, or the parent node could also be part of
            // the input, in which case it's unnecessary to try and parse its children, because a previous crawler would
            // have already foud the parent node.
            // Also check whether attemptCount == 0, which handles the case where the crawler is supposed to parse the root node at the start of the command, and it skips some array lockups
            fun getNodesForAttempt() = if(attemptCount == 0 || attemptPositionsFollowingVariable[startAttemptIndex + attemptCount]) nodes else nodesWithSpaces
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
            if(parentSpawner.baseResult == null) {
                // If the parent node is the root spawner, it's always removed because there's only one correct parsing attempt for root
                weightedSpawners.clear()
                if(childSpawner != null)
                    weightedSpawners += mutableListOf(childSpawner)
                return
            }
            // Exact conditions for where to cut off are more or less arbitrary, just see what works well
            val minLiteralCountForConfidence = 3
            if(literalNodeCount < minLiteralCountForConfidence)
                return


            if(literalNodeCount - parentSpawner.baseResult!!.literalNodeCount >= minLiteralCountForConfidence) {
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

        fun hasNewLiteralNodes(): Boolean = parentSpawner.baseResult == null || literalNodeCount > parentSpawner.baseResult!!.literalNodeCount
        fun newNodeCount(): Int = parsedNodeCount - (parentSpawner.baseResult?.parsedNodeCount ?: 0)

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
        semanticTokensCount += analyzingResult.semanticTokens.multilineTokenCount
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

    private inner class UnnecessaryAttemptDeduplicator {
        val nodeIdentifier = getNodeIdentifierForDispatcher(reader.dispatcher)
        val unnecessaryAttemptsMarker = BooleanArray(nodeIdentifier.idCount * attemptPositions.size)
        private fun getAttemptMarkerIndex(attemptPositionIndex: Int, attemptNode: CommandNode<CommandSource>): Int =
            attemptPositionIndex * nodeIdentifier.idCount + nodeIdentifier.getIdForNode(attemptNode)

        fun shouldSkipAttempt(attemptPositionIndex: Int, attemptRootNode: CommandNode<CommandSource>): Boolean =
            attemptRootNode.children.all {
                unnecessaryAttemptsMarker[getAttemptMarkerIndex(attemptPositionIndex, it)]
            }

        fun markUnnecessaryAttempt(attemptPositionIndex: Int, attemptRootNode: CommandNode<CommandSource>) {
            for(child in attemptRootNode.children) {
                unnecessaryAttemptsMarker[getAttemptMarkerIndex(attemptPositionIndex, child)] = true
            }
        }

        fun markTrailingNode(attemptPositionIndex: Int, lastNode: CommandNode<CommandSource>) {
            unnecessaryAttemptsMarker[getAttemptMarkerIndex(attemptPositionIndex, lastNode)] = true
        }
    }

    /**
     * Counts how many of each literal can be parsed at most starting with each node.
     *
     * The count for a node can be retrieved with [getLiteralCountsForNode] after calling [traverse] on the root node.
     */
    class NodeMaxLiteralCounter(private val nodeIdentifier: NodeIdentifier) {
        private val nodeTraversalStack = LinkedHashSet<CommandNode<CommandSource>>()

        /**
         * All entries represent a loop of nodes (or multiple loops, if they all have the same key),
         * where the key is the first loop node that was encountered and the value is a set of all nodes in the loop (including the key).
         *
         * i) The key must always be in the stack, because the loop is removed from the map after the key has been fully
         * processed and subsequently removed from the stack.
         *
         * ii) Two entries are not allowed to overlap, because they should be merged instead. The new key will be whichever key comes
         * first in the stack.
         */
        private val loopedDependants = mutableMapOf<CommandNode<CommandSource>, Set<CommandNode<CommandSource>>>()

        /**
         * The literal counts for each processed node.
         * @see getLiteralCountsForNode
         */
        private val nodeLiteralCounts = mutableMapOf<CommandNode<CommandSource>, UByteArray>()

        /**
         * Gets the literal counts for a node. The result is a [UByteArray], where the indices correspond to
         * the literal ids from [nodeIdentifier]. A value of 255 means a literal may match any amount of times
         * (because of loops).
         */
        fun getLiteralCountsForNode(node: CommandNode<CommandSource>): UByteArray = nodeLiteralCounts[node] ?: throw IllegalArgumentException("Tried retrieving literal counts for unprocessed node")

        fun traverse(node: CommandNode<CommandSource>): UByteArray? {
            var literalCounts = nodeLiteralCounts[node]
            if(literalCounts != null)
                return literalCounts

            // Don't change order for existing nodes
            if(!nodeTraversalStack.add(node)) {
                addLoop(node)
                return null
            }

            literalCounts = UByteArray(nodeIdentifier.literalIdCount)

            // Add children/redirect to literal counts
            val children = node.redirect?.children ?: node.children
            for(child in children) {
                val childLiteralCounts = traverse(child) ?: continue
                maxLiteralCounts(literalCounts, childLiteralCounts)
            }

            // Add self to literal counts
            if(node is LiteralCommandNode<*>) {
                val literalId = nodeIdentifier.getIdForLiteral(node.literal)
                addToLiteralCount(literalCounts, literalId)
            }

            processLoop(node, literalCounts)

            nodeLiteralCounts[node] = literalCounts
            // Pop stack and make sure it's valid
            assert(nodeTraversalStack.removeLast() == node)

            return literalCounts
        }

        private fun addLoop(duplicatedNode: CommandNode<CommandSource>) {
            val loopNodes = mutableSetOf<CommandNode<CommandSource>>()
            var topOverlappingLoop: Map.Entry<CommandNode<CommandSource>, Set<CommandNode<CommandSource>>>? = null

            // Build loopNodes list and check for other loops that overlap with it
            for(loopNode in nodeTraversalStack.reversed()) {
                if(topOverlappingLoop != null && loopNode in topOverlappingLoop.value) {
                    if(topOverlappingLoop.key == loopNode) {
                        // This must be the top node in the loop, any further nodes can't be part of this overlapping loop
                        topOverlappingLoop = null
                    }
                    // This node must already be in the overlapping loop, no need to go through the rest and check again
                    continue
                }
                val overlappingLoop = loopedDependants.entries.find { loopNode in it.value }
                if(overlappingLoop != null) {
                    loopNodes += overlappingLoop.value
                    loopedDependants.remove(overlappingLoop.key)
                    if(overlappingLoop.key != loopNode) {
                        // Only set top overlapping loop if there's at least one more node in it, so it will be removed again after
                        // the key has been processed
                        topOverlappingLoop = overlappingLoop
                    }
                } else {
                    loopNodes += loopNode
                }
                if(loopNode == duplicatedNode)
                    break
            }

            val newLoopKey = topOverlappingLoop?.key ?: duplicatedNode
            loopedDependants[newLoopKey] = loopNodes
        }

        private fun processLoop(loopKey: CommandNode<CommandSource>, keyLiteralCounts: UByteArray) {
            val loopNodes = loopedDependants.remove(loopKey) ?: return

            for(loopNode in loopNodes) {
                // In a loop, all nodes can refer to each other so they all have the same literal counts
                nodeLiteralCounts[loopNode] = keyLiteralCounts

                // All literals in the loop can appear an infinite number of times
                if(loopNode !is LiteralCommandNode<*>) continue
                val literalId = nodeIdentifier.getIdForLiteral(loopNode.literal)
                keyLiteralCounts[literalId] = LITERAL_COUNT_INFINITY
            }
        }

        private fun maxLiteralCounts(dest: UByteArray, src: UByteArray) {
            for(i in 0 until dest.size) {
                dest[i] = maxOf(dest[i], src[i])
            }
        }

        private fun addToLiteralCount(array: UByteArray, literalId: Int) {
            val initialCount = array[literalId]
            if(initialCount == LITERAL_COUNT_INFINITY)
                return
            array[literalId] = (initialCount.toInt() + 1).toUByte()
        }
    }

    class NodeIdentifier {
        private val serializedNodeIds = Object2IntOpenHashMap<SerializedNode>()
        private val assignedIds = Object2IntOpenHashMap<CommandNode<CommandSource>>()
        // Literals are given a second id that is used for calculating how many literals a given
        // node can match at maximum in the remaining input
        private val literalIds = Object2IntOpenHashMap<String>()

        val idCount get() = serializedNodeIds.size
        val literalIdCount get() = literalIds.size

        init {
            serializedNodeIds.defaultReturnValue(-1)
            assignedIds.defaultReturnValue(-1)
            literalIds.defaultReturnValue(-1)
        }

        fun getIdForNode(node: CommandNode<CommandSource>): Int {
            val id = assignedIds.getInt(node)
            if(id == -1)
                throw IllegalArgumentException("Tried retrieving id for unregistered node")
            return id
        }

        fun getIdForLiteral(literal: String): Int {
            val id = literalIds.getInt(literal)
            if(id == -1)
                throw IllegalArgumentException("Tried retrieving id for unregistered literal '${literal}'")
            return id
        }

        fun registerChildrenRecursive(parent: CommandNode<CommandSource>) {
            for(child in parent.children) {
                registerNode(child)
                registerChildrenRecursive(child)
            }
        }

        fun registerNode(node: CommandNode<CommandSource>) {
            val serialized = serializeNode(node)
            val serializedAssignedId = serializedNodeIds.getInt(serialized)
            val newNodeId: Int
            if(serializedAssignedId != -1)
                newNodeId = serializedAssignedId
            else {
                newNodeId = serializedNodeIds.size
                serializedNodeIds.put(serialized, newNodeId)
            }
            assignedIds.put(node, newNodeId)

            if(node is LiteralCommandNode)
                registerLiteral(node.literal)
        }

        fun registerLiteral(literal: String) {
            if(!literalIds.containsKey(literal))
                literalIds.put(literal, literalIds.size)
        }

        private fun serializeNode(node: CommandNode<CommandSource>): SerializedNode {
            fun <TType : ArgumentType<*>, TProperties : ArgumentSerializer.ArgumentTypeProperties<TType>> writeArgumentType(
                type: TType,
                serializer: ArgumentSerializer<TType, TProperties>,
                buf: PacketByteBuf
            ) {
                serializer.writePacket(serializer.getArgumentTypeProperties(type) as TProperties, buf)
            }

            val buf = PacketByteBufs.create()
            val typeId: Int
            val suggestionProvider: SuggestionProvider<CommandSource>?
            when(node) {
                is LiteralCommandNode -> {
                    typeId = LITERAL_NODE_TYPE_ID
                    buf.writeString(node.name)
                    suggestionProvider = null
                }
                is ArgumentCommandNode<CommandSource, *> -> {
                    val serializer = ArgumentTypes.get(node.type)
                    typeId = Registries.COMMAND_ARGUMENT_TYPE.getRawId(serializer)
                    writeArgumentType(node.type, serializer, buf)
                    suggestionProvider = node.customSuggestions
                }
                else -> throw IllegalArgumentException("Unexpected node type: $node")
            }
            val array = ByteArray(buf.readableBytes())
            buf.readBytes(array)
            return SerializedNode(typeId, array, suggestionProvider)
        }

        private data class SerializedNode(val typeId: Int, val data: ByteArray, val suggestionProvider: SuggestionProvider<CommandSource>?) {
            override fun equals(other: Any?): Boolean {
                if(this === other) return true
                if(javaClass != other?.javaClass) return false

                other as SerializedNode

                if(typeId != other.typeId) return false
                if(!data.contentEquals(other.data)) return false
                if(suggestionProvider != other.suggestionProvider) return false

                return true
            }

            override fun hashCode(): Int {
                var result = typeId
                result = 31 * result + data.contentHashCode()
                result = 31 * result + suggestionProvider.hashCode()
                return result
            }

        }
    }

    companion object {
        private const val STEPS_PER_CRAWLER_BEFORE_PUSH = 5
        private const val LITERAL_NODE_TYPE_ID = -1
        private const val LITERAL_COUNT_INFINITY: UByte = 255U
        private val macroLanguage = VanillaLanguage()
        private val processedDispatcherData = WeakHashMap<CommandDispatcher<CommandSource>, Pair<NodeIdentifier, NodeMaxLiteralCounter>>()
        private const val shouldCheckForTimeout = true
        private val timeoutDurationNs = TimeUnit.SECONDS.toNanos(1)
        private var isAnalyzingMacroForFirstTime = true

        private fun getNodeIdentifierForDispatcher(dispatcher: CommandDispatcher<CommandSource>): NodeIdentifier =
            processCommandDispatcher(dispatcher).first

        private fun getNodeMaxLiteralCounterForDispatcher(dispatcher: CommandDispatcher<CommandSource>): NodeMaxLiteralCounter =
            processCommandDispatcher(dispatcher).second

        private fun processCommandDispatcher(dispatcher: CommandDispatcher<CommandSource>): Pair<NodeIdentifier, NodeMaxLiteralCounter> {
            val cached = processedDispatcherData[dispatcher]
            if(cached != null)
                return cached

            val nodeIdentifier = NodeIdentifier()
            nodeIdentifier.registerChildrenRecursive(dispatcher.root)
            val nodeMaxLiteralCounter = NodeMaxLiteralCounter(nodeIdentifier)
            nodeMaxLiteralCounter.traverse(dispatcher.root)
            val pair = nodeIdentifier to nodeMaxLiteralCounter
            processedDispatcherData[dispatcher] = pair
            return pair
        }

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