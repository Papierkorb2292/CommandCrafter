package net.papierkorb2292.command_crafter.parser.helper

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.RootCommandNode
import it.unimi.dsi.fastutil.chars.CharSet
import net.minecraft.command.CommandSource
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.util.packrat.Cut
import net.minecraft.util.packrat.ParseResults
import net.minecraft.util.packrat.ParsingState
import net.minecraft.util.packrat.Symbol
import net.minecraft.util.packrat.Term
import net.papierkorb2292.command_crafter.editor.processing.MalformedParseErrorList
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs
import net.papierkorb2292.command_crafter.helper.getOrNull
import net.papierkorb2292.command_crafter.helper.runWithValue
import net.papierkorb2292.command_crafter.mixin.editor.CommandManagerAccessor

fun CommandNode<*>.visitChildrenRecursively(visitor: (CommandNode<*>) -> Unit) {
    visitor(this)
    for(child in children) {
        child.visitChildrenRecursively(visitor)
    }
}

val IS_BUILDING_CLIENTSIDE_COMMAND_TREE = ThreadLocal<Boolean>()

fun limitCommandTreeForSource(commandManager: CommandManager, source: ServerCommandSource): RootCommandNode<CommandSource> {
    val rootNode = RootCommandNode<ServerCommandSource>()
    val newCommandTreeMapping = mutableMapOf<CommandNode<ServerCommandSource>, CommandNode<ServerCommandSource>>(commandManager.dispatcher.root to rootNode)
    IS_BUILDING_CLIENTSIDE_COMMAND_TREE.runWithValue(true) {
        CommandManagerAccessor.callDeepCopyNodes(
            commandManager.dispatcher.root,
            rootNode,
            source,
            newCommandTreeMapping
        )
    }
    @Suppress("UNCHECKED_CAST")
    return rootNode as RootCommandNode<CommandSource>
}

fun <S> CommandNode<S>.resolveRedirects(): CommandNode<S> {
    var node = this
    while(node.redirect != null)
        node = node.redirect
    return node
}

fun <S> delegatingTerm(callback: (state: ParsingState<S>, results: ParseResults, cut: Cut) -> Term<S>): Term<S> =
    Term<S> { state, results, cut -> callback(state, results, cut).matches(state, results, cut) }

fun wrapTermAddEntryRanges(term: Term<StringReader>): Term<StringReader> = delegatingTerm { state, results, cut ->
        val reader = state.reader
        val stringRangeTreeBuilderArg = PackratParserAdditionalArgs.nbtStringRangeTreeBuilder.getOrNull()
        if(stringRangeTreeBuilderArg != null) {
            val node = stringRangeTreeBuilderArg.stringRangeTreeBuilder.peekNode()
            if(node != null) {
                val cursor = reader.cursor
                reader.skipWhitespace()
                node.addRangeBetweenEntries(StringRange(cursor, reader.cursor))
                reader.setCursor(cursor)
            }
        }
        term
    }

fun <TElement> wrapTermSkipToNextEntryIfMalformed(term: Term<StringReader>, entryDelimiters: CharSet, elementName: Symbol<TElement>, errorDefaultProvider: () -> TElement): Term<StringReader> =
    wrapTermSkipToNextEntryIfMalformedWithIllegalCharacters(term, entryDelimiters, CharSet.of(), elementName, errorDefaultProvider)

fun <TElement> wrapTermSkipToNextEntryIfMalformedWithIllegalCharacters(term: Term<StringReader>, entryDelimiters: CharSet, illegalCharacters: CharSet, elementName: Symbol<TElement>, errorDefaultProvider: () -> TElement): Term<StringReader> =
    Term<StringReader> { state, results, cut ->
        val reader = state.reader
        // Start a new scope for errors such that suggestions for a malformed input can still be shown even though the term matches
        val closeErrorListScopeCallback = (state.errors as? MalformedParseErrorList)?.startMalformedScope()
        val originalMatches = term.matches(state, results, cut)
        if(!PackratParserAdditionalArgs.shouldAllowMalformed())
            return@Term originalMatches
        if(!originalMatches)
            results.put(elementName, errorDefaultProvider())
        while(reader.canRead() && !entryDelimiters.contains(reader.peek())) {
            if(illegalCharacters.contains(reader.peek())) {
                closeErrorListScopeCallback?.invoke(reader.cursor)
                return@Term false
            }
            reader.skip()
        }
        closeErrorListScopeCallback?.invoke(reader.cursor)
        // Since malformed elements are allowed, the term always matches
        true
    }