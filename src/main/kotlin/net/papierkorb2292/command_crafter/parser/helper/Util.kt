package net.papierkorb2292.command_crafter.parser.helper

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.CommandContextBuilder
import com.mojang.brigadier.context.ParsedCommandNode
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.RootCommandNode
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.Dynamic
import it.unimi.dsi.fastutil.chars.CharSet
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.StringTag
import net.minecraft.resources.RegistryOps
import net.minecraft.server.permissions.PermissionSetSupplier
import net.minecraft.util.parsing.packrat.*
import net.minecraft.util.parsing.packrat.commands.CommandArgumentParser.RegistryAwareState
import net.minecraft.util.parsing.packrat.commands.CommandParserState
import net.papierkorb2292.command_crafter.editor.processing.MalformedParseErrorList
import net.papierkorb2292.command_crafter.editor.processing.TokenInfo
import net.papierkorb2292.command_crafter.editor.processing.TokenType
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs
import net.papierkorb2292.command_crafter.helper.getOrNull
import net.papierkorb2292.command_crafter.helper.runWithValue
import net.papierkorb2292.command_crafter.mixin.editor.SuggestionBoxAccessor
import net.papierkorb2292.command_crafter.mixin.parser.RegistryOpsAccessor

fun CommandNode<*>.visitChildrenRecursively(visitor: (CommandNode<*>) -> Unit) {
    visitor(this)
    for(child in children) {
        child.visitChildrenRecursively(visitor)
    }
}

val IS_BUILDING_CLIENTSIDE_COMMAND_TREE = ThreadLocal<Boolean>()

fun <S> limitCommandTreeForSource(dispatcher: CommandDispatcher<S>, parseContext: PermissionSetSupplier): RootCommandNode<S> {
    val rootNode = RootCommandNode<S>()
    val newCommandTreeMapping = mutableMapOf<CommandNode<S>, CommandNode<S>>(dispatcher.root to rootNode)
    IS_BUILDING_CLIENTSIDE_COMMAND_TREE.runWithValue(true) {
        @Suppress("UNCHECKED_CAST")
        SuggestionBoxAccessor.callFillUsableCommands(
            dispatcher.root,
            rootNode,
            parseContext as S, // Can be cast like this, because it is only given to PermissionProviderChecks or CommandBox.DEFAULT_ARGUMENT_PREDICATE, otherwise CommandBox.convertNodePermissionCheck would fail
            newCommandTreeMapping
        )
    }
    return rootNode
}

fun <S> CommandNode<S>.resolveRedirect(): CommandNode<S> = redirect ?: this

fun <S> CommandContext<S>.getContextAtCursor(cursor: Int): CommandContext<S>? {
    var context: CommandContext<S>? = this
    while(context != null) {
        if(context.nodes.isNotEmpty()) {
            if(cursor <= context.range.end + (context.nodes.last() as CursorOffsetContainer).getCursorOffset()) {
                // Found the right context
                return context
            }
        }
        context = context.child
    }
    return null
}

fun <S> CommandContext<S>.getNodeAtCursor(cursor: Int): ParsedCommandNode<S>? =
    nodes.firstOrNull { node ->
        cursor <= node.range.end + (node as CursorOffsetContainer).getCursorOffset()
    }

fun <S> CommandContextBuilder<S>.getLastNodeWithRedirects(): CommandNode<S> {
    val lastChild = this.lastChild
    return lastChild.nodes.lastOrNull()?.node ?: lastChild.rootNode
}

fun <S: Any> delegatingTerm(callback: (state: ParseState<S>, results: Scope, cut: Control) -> Term<S>): Term<S> =
    Term<S> { state, results, cut -> callback(state, results, cut).parse(state, results, cut) }

fun <TState: CommandParserState> wrapTermAddEntryRanges(term: Term<TState>): Term<TState> = delegatingTerm { state, results, cut ->
        val reader = state.input().reader()
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

fun <TElement: Any, TState: CommandParserState> wrapTermSkipToNextEntryIfMalformed(term: Term<TState>, entryDelimiters: CharSet, elementName: Atom<TElement>, errorDefaultProvider: (TState) -> TElement): Term<TState> =
    wrapTermSkipToNextEntryIfMalformedWithIllegalCharacters(term, entryDelimiters, CharSet.of(), elementName, errorDefaultProvider)

fun <TElement: Any, TState: CommandParserState> wrapTermSkipToNextEntryIfMalformedWithIllegalCharacters(term: Term<TState>, entryDelimiters: CharSet, illegalCharacters: CharSet, elementName: Atom<TElement>, errorDefaultProvider: (TState) -> TElement): Term<TState> =
    Term<TState> { state, results, cut ->
        val reader = state.input().reader()
        // Start a new scope for errors such that suggestions for a malformed input can still be shown even though the term matches
        val closeErrorListScopeCallback = (state.errorCollector() as? MalformedParseErrorList)?.startMalformedScope()
        val originalMatches = term.parse(state, results, cut)
        if(!PackratParserAdditionalArgs.shouldAllowMalformed())
            return@Term originalMatches
        if(!originalMatches)
            results.put(elementName, errorDefaultProvider(state.input()))
        while(reader.canRead() && !entryDelimiters.contains(reader.peek())) {
            if(illegalCharacters.contains(reader.peek())) {
                closeErrorListScopeCallback?.invoke(reader.cursor, true)
                return@Term false
            }
            reader.skip()
        }
        closeErrorListScopeCallback?.invoke(reader.cursor, originalMatches)
        // Since malformed elements are allowed, the term always matches
        true
    }

fun <TState: CommandParserState> malformedDispatchingTerm(normalTerm: Term<TState>, allowMalformedTerm: Term<TState>): Term<TState> =
    Term { state, results, cut ->
        if(!PackratParserAdditionalArgs.shouldAllowMalformed())
            normalTerm.parse(state, results, cut)
        else
            allowMalformedTerm.parse(state, results, cut)
    }

fun <TState: CommandParserState> wrapTermWithSemanticToken(term: Term<TState>, tokenProvider: (ParseState<TState>, Scope, StringRange) -> TokenInfo?) = Term { state, results, cut ->
    val start = state.mark()
    val matches = term.parse(state, results, cut)
    if(matches) {
        val analyzingResultArg = PackratParserAdditionalArgs.analyzingResult.getOrNull() ?: return@Term matches
        val range = StringRange(start, state.mark())
        val token = tokenProvider(state, results, range) ?: return@Term matches
        analyzingResultArg.analyzingResult.semanticTokens.addMultiline(range, token.type, token.modifiers)
    }
    matches
}

fun <TState: CommandParserState> wrapTermWithSemanticToken(term: Term<TState>, tokenType: TokenType) = wrapTermWithSemanticToken(term) { _, _, _ -> TokenInfo(tokenType, 0) }

fun <T: Any, TState: CommandParserState> repeatUntilInputEnd(term: NamedRule<TState, T>, listAtom: Atom<List<T>>) = object : Term<TState> {
    override fun parse(
        state: ParseState<TState>,
        scope: Scope,
        control: Control,
    ): Boolean {
        val start = state.mark()
        val result = mutableListOf<T>()
        val input = state.input()
        while(canContinue(input.reader())) {
            // Doesn't need to branch args, because the term isn't optional
            val child = state.parse(term)
            if(child == null) {
                state.restore(start)
                return false
            }
            result += child
        }
        scope.put(listAtom, result)
        return true
    }

    private fun canContinue(input: StringReader): Boolean {
        input.skipWhitespace()
        return input.canRead() || PackratParserAdditionalArgs.shouldAllowMalformed() // Allow malformed to also add suggestions at the end of the string
    }
}

val REGISTRY_AWARE_STATE_CODEC: Codec<RegistryAwareState> = Codec.PASSTHROUGH.flatXmap({ dynamic ->
    val ops = dynamic.ops as? RegistryOps<*> ?: return@flatXmap DataResult.error { "Not a registry ops" }
    Codec.STRING.parse(dynamic).map { input ->
        RegistryAwareState.Simple(StringReader(input), (ops as RegistryOpsAccessor).getterProvider)
    }
}, { DataResult.success(Dynamic(NbtOps.INSTANCE, StringTag.valueOf(it.reader().string))) })