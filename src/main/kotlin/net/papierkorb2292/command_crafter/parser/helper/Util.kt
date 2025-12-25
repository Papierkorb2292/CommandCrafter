package net.papierkorb2292.command_crafter.parser.helper

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.context.CommandContextBuilder
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.RootCommandNode
import it.unimi.dsi.fastutil.chars.CharSet
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.Commands
import net.minecraft.commands.CommandSourceStack
import net.minecraft.util.parsing.packrat.Control
import net.minecraft.util.parsing.packrat.Scope
import net.minecraft.util.parsing.packrat.ParseState
import net.minecraft.util.parsing.packrat.Atom
import net.minecraft.util.parsing.packrat.Term
import net.papierkorb2292.command_crafter.editor.processing.MalformedParseErrorList
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs
import net.papierkorb2292.command_crafter.helper.getOrNull
import net.papierkorb2292.command_crafter.helper.runWithValue
import net.papierkorb2292.command_crafter.mixin.editor.CommandsAccessor

fun CommandNode<*>.visitChildrenRecursively(visitor: (CommandNode<*>) -> Unit) {
    visitor(this)
    for(child in children) {
        child.visitChildrenRecursively(visitor)
    }
}

val IS_BUILDING_CLIENTSIDE_COMMAND_TREE = ThreadLocal<Boolean>()

fun limitCommandTreeForSource(commandManager: Commands, source: CommandSourceStack): RootCommandNode<SharedSuggestionProvider> {
    val rootNode = RootCommandNode<CommandSourceStack>()
    val newCommandTreeMapping = mutableMapOf<CommandNode<CommandSourceStack>, CommandNode<CommandSourceStack>>(commandManager.dispatcher.root to rootNode)
    IS_BUILDING_CLIENTSIDE_COMMAND_TREE.runWithValue(true) {
        CommandsAccessor.callFillUsableCommands(
            commandManager.dispatcher.root,
            rootNode,
            source,
            newCommandTreeMapping
        )
    }
    @Suppress("UNCHECKED_CAST")
    return rootNode as RootCommandNode<SharedSuggestionProvider>
}

fun <S> CommandNode<S>.resolveRedirects(): CommandNode<S> {
    var node = this
    while(node.redirect != null)
        node = node.redirect
    return node
}

fun <S> CommandContextBuilder<S>.getLastNodeWithRedirects(): CommandNode<S> {
    val lastChild = this.lastChild
    return lastChild.nodes.lastOrNull()?.node ?: lastChild.rootNode
}

fun <S: Any> delegatingTerm(callback: (state: ParseState<S>, results: Scope, cut: Control) -> Term<S>): Term<S> =
    Term<S> { state, results, cut -> callback(state, results, cut).parse(state, results, cut) }

fun wrapTermAddEntryRanges(term: Term<StringReader>): Term<StringReader> = delegatingTerm { state, results, cut ->
        val reader = state.input()
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

fun <TElement: Any> wrapTermSkipToNextEntryIfMalformed(term: Term<StringReader>, entryDelimiters: CharSet, elementName: Atom<TElement>, errorDefaultProvider: () -> TElement): Term<StringReader> =
    wrapTermSkipToNextEntryIfMalformedWithIllegalCharacters(term, entryDelimiters, CharSet.of(), elementName, errorDefaultProvider)

fun <TElement: Any> wrapTermSkipToNextEntryIfMalformedWithIllegalCharacters(term: Term<StringReader>, entryDelimiters: CharSet, illegalCharacters: CharSet, elementName: Atom<TElement>, errorDefaultProvider: () -> TElement): Term<StringReader> =
    Term<StringReader> { state, results, cut ->
        val reader = state.input()
        // Start a new scope for errors such that suggestions for a malformed input can still be shown even though the term matches
        val closeErrorListScopeCallback = (state.errorCollector() as? MalformedParseErrorList)?.startMalformedScope()
        val originalMatches = term.parse(state, results, cut)
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