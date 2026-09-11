package net.papierkorb2292.command_crafter.parser.number_provider

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.datafixers.util.Either
import com.mojang.serialization.*
import com.mojang.serialization.codecs.RecordCodecBuilder
import it.unimi.dsi.fastutil.chars.CharList
import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.NbtOps
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.resources.RegistryOps
import net.minecraft.resources.ResourceKey
import net.minecraft.util.parsing.packrat.*
import net.minecraft.util.parsing.packrat.commands.Grammar
import net.minecraft.util.parsing.packrat.commands.ResourceSuggestion
import net.minecraft.util.parsing.packrat.commands.StringReaderTerms
import net.minecraft.util.parsing.packrat.commands.TagParseRule
import net.papierkorb2292.command_crafter.editor.debugger.helper.plus
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.IdArgumentTypeAnalyzer
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.editor.processing.TokenType
import net.papierkorb2292.command_crafter.editor.processing.TokenType.Companion.PARAMETER
import net.papierkorb2292.command_crafter.editor.processing.codecmod.ExtraDecoderBehavior
import net.papierkorb2292.command_crafter.editor.processing.codecmod.conditionalDecode
import net.papierkorb2292.command_crafter.editor.processing.codecmod.decodeParent
import net.papierkorb2292.command_crafter.editor.processing.codecmod.onlyContextBehavior
import net.papierkorb2292.command_crafter.editor.processing.helper.ActualSyntaxNode
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.MalformedStringDecoderAnalyzing
import net.papierkorb2292.command_crafter.helper.getOrNull
import net.papierkorb2292.command_crafter.helper.memoizeLast
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.NamespacedIdRule
import net.papierkorb2292.command_crafter.parser.helper.repeatUntilInputEnd
import net.papierkorb2292.command_crafter.parser.helper.wrapTermWithSemanticToken
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Range
import java.util.concurrent.CompletableFuture
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.jvm.optionals.getOrNull

/**
 * A transpiler from a custom expression syntax to number providers
 */
object TermNumberProvider {
    val DISALLOW_EXPRESSIONS = ThreadLocal<Boolean>()

    fun register() {
        Registry.register(
            BuiltInRegistries.CONTEXT_INT_PROVIDER_TYPE,
            Identifier.fromNamespaceAndPath("command_crafter", "term"),
            getProviderCodec(INT_NUMBER_PROVIDER_TYPE)
        )
        Registry.register(
            BuiltInRegistries.CONTEXT_FLOAT_PROVIDER_TYPE,
            Identifier.fromNamespaceAndPath("command_crafter", "term"),
            getProviderCodec(FLOAT_NUMBER_PROVIDER_TYPE)
        )
    }

    private val VAR_NAME_CODEC = Codec.STRING.validate { name ->
        if(name.isEmpty()) return@validate DataResult.error { "Variable name must not be empty" }
        for((index, c) in name.withIndex()) {
            if(!isAllowedInVarName(c, index == 0)) {
                return@validate return@validate DataResult.error { "Illegal character in variable name: '$c'" }
            }
        }
        DataResult.success(name)
    }

    private val AVAILABLE_VARS_DECODER = object : Decoder<Map<String, Any>> {
        override fun <T> decode(ops: DynamicOps<T>, input: T): DataResult<com.mojang.datafixers.util.Pair<Map<String, Any>, T>> =
            ops.getMapValues(input).map { entries ->
                com.mojang.datafixers.util.Pair.of(entries.collect(Collectors.toMap({ ops.getStringValue(it.first).orThrow }, { it.second })), ops.empty())
            }
    }

    private fun <TNumberProvider: Any> getExpressionCodec(type: NumberProviderType<TNumberProvider>, isRoot: Boolean): Codec<Expression<TNumberProvider>> {
        // The grammar needs dynamic ops to resolve number provider references, but the grammar can be cached if the same number provider contains multiple terms
        val grammarFactory = { ops: DynamicOps<*> -> buildExpressionGrammar(type, ops) }.memoizeLast()

        // Use a special decoder for the language server, which puts errors at the correct location in the string
        // and offers analyzing (semantic tokens, completions, etc.)
        val varsDecoder = (if(isRoot) AVAILABLE_VARS_DECODER.fieldOf("inputs").decoder() else AVAILABLE_VARS_DECODER).decodeParent().onlyContextBehavior()
        val stringAnalyzing = MalformedStringDecoderAnalyzing({ it.ops to varsDecoder.parse(it).result().getOrNull() }, { (ops, vars), result, behavior, reader, string, analyzingBehavior ->
            val grammar = buildExpressionGrammar(type, ops, if(vars != null) AnalyzingVariableData(vars, analyzingBehavior) else null)

            PackratParserAdditionalArgs.analyzingResult.set(PackratParserAdditionalArgs.AnalyzingResultBranchingArgument(result.copyInput()))
            PackratParserAdditionalArgs.setupFurthestAnalyzingResultStart()
            PackratParserAdditionalArgs.allowMalformed.set(true)

            try {
                try {
                    grammar.parseForCommands(reader)
                } catch(_: CommandSyntaxException) {}
                PackratParserAdditionalArgs.popAnalyzingResult(result, StringRange(0, string.content.length))
            } finally {
                PackratParserAdditionalArgs.allowMalformed.remove()
                PackratParserAdditionalArgs.analyzingResult.remove()
                PackratParserAdditionalArgs.furthestAnalyzingResult.remove()
            }
        })
        val analyzingDecoder = stringAnalyzing.wrapDecoder(Codec.PASSTHROUGH.flatMap { dynamic ->
            Codec.STRING.parse(dynamic).flatMap { string ->
                try {
                    val reader = StringReader(string)
                    val expression = grammarFactory(dynamic.ops).parseForCommands(reader)
                    stringAnalyzing.onParsed()
                    DataResult.success(expression)
                } catch(e: CommandSyntaxException) {
                    stringAnalyzing.onParsed(e.cursor, e.message)
                    DataResult.error { e.message }
                }
            }
        })

        return Codec.PASSTHROUGH.flatXmap(
            { dynamic ->
                Codec.STRING.parse(dynamic).flatMap { string ->
                    try {
                        val reader = StringReader(string)
                        DataResult.success(grammarFactory(dynamic.ops).parseForCommands(reader)) // The grammar always reads until the end of the string
                    } catch(e: CommandSyntaxException) {
                        DataResult.error { e.message }
                    }
                }
            },
            { throw NotImplementedError("Terms can't be encoded") }
        ).conditionalDecode({ VanillaLanguage.IS_ANALYZING_COMMANDS.getOrNull() != true }, analyzingDecoder)
    }

    /**
     * Gets the map codec that can decode a term into normal number providers. This codec can't be used for encoding.
     */
    private fun <TNumberProvider: Any> getProviderCodec(type: NumberProviderType<TNumberProvider>): MapCodec<TNumberProvider> {
        val inputCodec = Codec.either(getExpressionCodec(type, false), type.inlineCodec).xmap(
            { either -> either.map({ it }, { DirectExpression(Holder.direct(it)) }) },
            Either<Expression<TNumberProvider>, TNumberProvider>::left
        )
        return RecordCodecBuilder.mapCodec {
            it.group(
                getExpressionCodec(type, true).fieldOf("term").forGetter { throw NotImplementedError("Terms can't be encoded") },
                Codec.unboundedMap(VAR_NAME_CODEC, inputCodec).optionalFieldOf("inputs", mapOf()).forGetter { throw NotImplementedError("Terms can't be encoded") },
            ).apply(it, ::ParsedProvider)
        }.flatXmap(
            { parsedProvider -> parsedProvider.resolve(type).map {
                holder -> holder.unwrap().map(
                { type.referenceWrapper(holder) }, // Can't return reference holders directly, so wrap them in another provider instead (for example in a sum)
                { it }
                )
            } },
            { throw NotImplementedError("Terms can't be encoded") }
        ).conditionalDecode({ DISALLOW_EXPRESSIONS.getOrNull() != true }, object : MapDecoder<TNumberProvider> {
            override fun <T> decode(
                ops: DynamicOps<T>,
                input: MapLike<T>,
            ): DataResult<TNumberProvider> = DataResult.error { "Expressions are not allowed here, they're not covered by the transpiler" }

            override fun <T> compressor(ops: DynamicOps<T>) = null
            override fun <T> keys(ops: DynamicOps<T>) = Stream.empty<T>()
        })
    }

    private val INCORRECT_ARG_COUNT_EXCEPTION = DynamicCommandExceptionType { Component.literal("Incorrect number of arguments for function '$it'") }

    private fun <TNumberProvider: Any> buildExpressionGrammar(type: NumberProviderType<TNumberProvider>, ops: DynamicOps<*>, analyzingVariables: AnalyzingVariableData<*>? = null): Grammar<Expression<TNumberProvider>> {
        val dict = Dictionary<StringReader>()

        // This term allows any amount of standalone terms with infix operators between them
        val operatorTermAtom = Atom<Expression<TNumberProvider>>("operator_term")
        // This term doesn't allow infix operators
        val standaloneTermAtom = Atom<Expression<TNumberProvider>>("standalone_term")
        // Like operator_term, but always parses until the end of the string
        val rootTermAtom = Atom<Expression<TNumberProvider>>("root_term")

        val infixAtom = Atom<NumberProviderInfix<TNumberProvider>>("infix")
        dict.put(infixAtom, Term.alternative(
            *type.infixOperations.map { infix ->
                Term.sequence(StringReaderTerms.character(infix.name), Term.cut(), Term.marker(infixAtom, infix))
            }.toTypedArray()
        )) { scope ->
            scope.getOrThrow(infixAtom)
        }

        val infixTermPairAtom = Atom<Pair<NumberProviderInfix<TNumberProvider>, Expression<TNumberProvider>>>("infix_term_pair")
        val infixTermPairRule = dict.put(infixTermPairAtom, Term.sequence(
            dict.named(infixAtom),
            dict.named(standaloneTermAtom)
        )) { scope ->
            scope.getOrThrow(infixAtom) to scope.getOrThrow(standaloneTermAtom)
        }

        val infixTermPairListAtom = Atom<List<Pair<NumberProviderInfix<TNumberProvider>, Expression<TNumberProvider>>>>("infix_term_pair_list")

        val operatorTermRule = dict.put(operatorTermAtom, Term.sequence(
            dict.named(standaloneTermAtom),
            Term.repeated(infixTermPairRule, infixTermPairListAtom)
        )) { scope ->
            buildInfixExpression(scope.getOrThrow(standaloneTermAtom), scope.getOrThrow(infixTermPairListAtom))
        }

        val parenthesesAtom = Atom<Expression<TNumberProvider>>("parentheses")
        dict.put(parenthesesAtom, Term.sequence(
            StringReaderTerms.character('('),
            Term.cut(),
            dict.named(operatorTermAtom),
            StringReaderTerms.character(')')
        )) { scope ->
            scope.getAnyOrThrow(operatorTermAtom)
        }

        val nbtAtom = Atom<Dynamic<*>>("tag")
        dict.put(nbtAtom, TagParseRule(NbtOps.INSTANCE))

        // Numbers are parsed as SNBT so we get all the nice syntax
        val numberAtom = Atom<Expression<TNumberProvider>>("number")
        dict.putComplex(numberAtom, dict.named(nbtAtom)) { state ->
            val result = type.numberDecoder.decode(state.scope().getOrThrow(nbtAtom))
            if(result.isSuccess) {
                DirectExpression(Holder.direct(result.result().get().first))
            } else {
                state.errorCollector().store(state.mark(), result.error().get().message())
                null
            }
        }

        val negatedTerm = Atom<Expression<TNumberProvider>>("negated")
        dict.put(negatedTerm, dict.named(standaloneTermAtom)) { scope ->
            UnaryExpression(scope.getOrThrow(standaloneTermAtom), type.negateFactory)
        }

        val referenceIdAtom = Atom<Identifier>("reference_id")
        val referenceIdRule = dict.put(referenceIdAtom, NamespacedIdRule)
        val referenceHolderAtom = Atom<Holder<TNumberProvider>>("holder")
        if(ops is RegistryOps<*>)
            dict.put(referenceHolderAtom, NumberProviderReferenceRule(referenceIdRule, ops, type.registryKey))
        else
            dict.put(referenceHolderAtom, Term.fail("No registry ops")) { throw AssertionError() }
        val referenceAtom = Atom<Expression<TNumberProvider>>("reference")
        dict.put(referenceAtom, dict.named(referenceHolderAtom)) { scope ->
            DirectExpression(scope.getOrThrow(referenceHolderAtom))
        }

        val functionNameAtom = Atom<NumberProviderFunction<TNumberProvider>>("function_name")
        dict.put(functionNameAtom, wrapTermWithSemanticToken(Term.alternative(
            *type.functions.map { function ->
                Term.sequence(StringReaderTerms.word(function.name), Term.cut(), Term.marker(functionNameAtom, function))
            }.toTypedArray()), TokenType.MACRO)
        ) { scope ->
            scope.getOrThrow(functionNameAtom)
        }
        val functionArgs = Atom<List<Expression<TNumberProvider>>>("function_args")
        val functionCallAtom = Atom<Expression<TNumberProvider>>("function_call")
        dict.putComplex(functionCallAtom, Term.sequence(
            dict.named(functionNameAtom),
            StringReaderTerms.character('('),
            Term.repeatedWithTrailingSeparator(operatorTermRule, functionArgs, StringReaderTerms.character(',')),
            StringReaderTerms.character(')')
        )) { state ->
            val function = state.scope().getOrThrow(functionNameAtom)
            val args = state.scope().getOrThrow(functionArgs)
            if(function.argumentCountMatcher(args.size))
                CompoundExpression(args, function.factory)
            else {
                state.errorCollector().store(state.mark(), DelayedException.create(INCORRECT_ARG_COUNT_EXCEPTION, function.name))
                null
            }
        }

        val variableNameAtom = Atom<String>("variable_name")
        dict.put(variableNameAtom, VariableNameParseRule(analyzingVariables))
        val variableAtom = Atom<Expression<TNumberProvider>>("variable")
        dict.put(variableAtom, dict.named(variableNameAtom)) { scope ->
            VariableExpression(scope.getOrThrow(variableNameAtom))
        }

        dict.put(standaloneTermAtom, Term.alternative(
            Term.sequence(NUMBER_LOOKAHEAD, dict.named(numberAtom)), // Don't cut here, because '-' still has one other case
            Term.sequence(StringReaderTerms.character('-'), Term.cut(), dict.named(negatedTerm)),
            dict.named(referenceAtom),
            dict.named(functionCallAtom),
            dict.named(variableAtom),
            dict.named(parenthesesAtom)
        )) { scope ->
            scope.getAnyOrThrow(numberAtom, negatedTerm, referenceAtom, functionCallAtom, variableAtom, parenthesesAtom)
        }

        val rootRule = dict.put(rootTermAtom, Term.sequence(
            dict.named(standaloneTermAtom),
            repeatUntilInputEnd(infixTermPairRule, infixTermPairListAtom)
        )) { scope ->
            buildInfixExpression(scope.getOrThrow(standaloneTermAtom), scope.getOrThrow(infixTermPairListAtom))
        }

        return Grammar(dict, rootRule)
    }

    /**
     * Recursively searches for the infix with the highest precedence level and builds it
     */
    private fun <TNumberProvider : Any> buildInfixExpression(start: Expression<TNumberProvider>, infixTerms: List<Pair<NumberProviderInfix<TNumberProvider>, Expression<TNumberProvider>>>): Expression<TNumberProvider> {
        if(infixTerms.isEmpty())
            return start
        if(infixTerms.size == 1)
            return BinaryExpression(start, infixTerms[0].second, infixTerms[0].first.factory)
        var nextInfixIndex = -1
        var currentPrecedenceLevel = 0
        for((index, infix) in infixTerms.withIndex()) {
            // If the precedenceLevel is larger, or it is equal but the infix comes later in the string,
            // the infix should be evaluated later. So it is used as the outer wrapper around the left and the right expression.
            if(infix.first.precedenceLevel >= currentPrecedenceLevel) {
                nextInfixIndex = index
                currentPrecedenceLevel = infix.first.precedenceLevel
            }
        }
        val (infix, rightStart) = infixTerms[nextInfixIndex]
        return BinaryExpression(
            buildInfixExpression(start, infixTerms.subList(0, nextInfixIndex)),
            buildInfixExpression(rightStart, infixTerms.subList(nextInfixIndex + 1, infixTerms.size)),
            infix.factory
        )
    }

    private val NUMBER_LOOKAHEAD = Term.positiveLookahead(object : StringReaderTerms.TerminalCharacters(CharList.of()) {
        override fun isAccepted(value: Char): Boolean = when(value) {
            '+', '-', '.', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> true
            else -> false
        }
    })

    private fun isAllowedInVarName(c: Char, isFirst: Boolean): Boolean = (c in 'a'..'z') || (c in 'A'..'Z') || (!isFirst && c in '0'..'9') || c == '_'

    /**
     * Helper class to recursively substitute variables in an expression and detect cycles
     */
    private class SubstitutionResolver<TNumberProvider: Any>(
        private val inputs: Map<String, Expression<TNumberProvider>>,
        type: NumberProviderType<TNumberProvider>,
    ) {
        private val instantiatedVariables = mutableMapOf<String, DataResult<Holder<TNumberProvider>>>()
        private val startedVariables = LinkedHashSet<String>()

        init {
            for((constant, value) in type.constants)
                instantiatedVariables[constant] = DataResult.success(Holder.direct(value))
        }

        fun resolveVariable(name: String) : DataResult<Holder<TNumberProvider>> {
            val instantiated = instantiatedVariables[name]
            if(instantiated != null)
                return instantiated
            if(startedVariables.contains(name)) {
                // Detected cycle
                val error = "Cyclic dependency: ${startedVariables.joinToString(" -> ") { "'$it'" }} -> '$name'" // Build error now, because startedVariables will change
                return DataResult.error { error }
            }
            val term = inputs[name] ?: return DataResult.error { "Unknown input '$name'" }
            startedVariables.addLast(name)
            val newInstance = instantiateTerm(term)
            assert(startedVariables.removeLast() == name)
            instantiatedVariables[name] = newInstance
            return newInstance
        }

        fun instantiateTerm(expression: Expression<TNumberProvider>): DataResult<Holder<TNumberProvider>> {
            return expression.instantiate(this)
        }
    }

    private data class ParsedProvider<TNumberProvider: Any>(
        private val rootExpression: Expression<TNumberProvider>,
        private val inputs: Map<String, Expression<TNumberProvider>>,
    ) {
        fun resolve(type: NumberProviderType<TNumberProvider>): DataResult<Holder<TNumberProvider>> =
            SubstitutionResolver(inputs, type).instantiateTerm(rootExpression)
    }

    private interface Expression<TNumberProvider: Any> {
        fun instantiate(substitutions: SubstitutionResolver<TNumberProvider>): DataResult<Holder<TNumberProvider>>
    }

    private data class DirectExpression<TNumberProvider: Any>(val provider: Holder<TNumberProvider>) : Expression<TNumberProvider> {
        override fun instantiate(substitutions: SubstitutionResolver<TNumberProvider>): DataResult<Holder<TNumberProvider>> =
            DataResult.success(provider)
    }

    private data class CompoundExpression<TNumberProvider: Any>(val children: List<Expression<TNumberProvider>>, val factory: (List<Holder<TNumberProvider>>) -> TNumberProvider) : Expression<TNumberProvider> {
        override fun instantiate(substitutions: SubstitutionResolver<TNumberProvider>): DataResult<Holder<TNumberProvider>> {
            var instantiatedChildren = DataResult.success(listOf<Holder<TNumberProvider>>())
            for(child in children) {
                val childInstance = child.instantiate(substitutions)
                instantiatedChildren = instantiatedChildren.apply2({ prev, new  -> prev + new }, childInstance)
            }
            return instantiatedChildren.mapOrElse(
                { DataResult.success(Holder.direct(factory(it)))},
                { DataResult.error(it::message)}
            )
        }
    }

    private data class BinaryExpression<TNumberProvider: Any>(val first: Expression<TNumberProvider>, val second: Expression<TNumberProvider>, val factory: (Holder<TNumberProvider>, Holder<TNumberProvider>) -> TNumberProvider) : Expression<TNumberProvider> {
        override fun instantiate(substitutions: SubstitutionResolver<TNumberProvider>): DataResult<Holder<TNumberProvider>> =
            first.instantiate(substitutions).apply2(factory, second.instantiate(substitutions)).mapOrElse(
                { DataResult.success(Holder.direct(it))},
                { DataResult.error(it::message)}
            )
    }

    private data class UnaryExpression<TNumberProvider: Any>(val child: Expression<TNumberProvider>, val factory: (Holder<TNumberProvider>) -> TNumberProvider) : Expression<TNumberProvider> {
        override fun instantiate(substitutions: SubstitutionResolver<TNumberProvider>): DataResult<Holder<TNumberProvider>> =
            child.instantiate(substitutions).map { Holder.direct(factory(it)) }
    }

    private data class VariableExpression<TNumberProvider: Any>(val variableName: String) : Expression<TNumberProvider> {
        override fun instantiate(substitutions: SubstitutionResolver<TNumberProvider>): DataResult<Holder<TNumberProvider>> =
            substitutions.resolveVariable(variableName)
    }

    class VariableNameParseRule<T: Any>(
        private val analyzingVariables: AnalyzingVariableData<T>? = null
    ) : Rule<StringReader, String> {
        override fun parse(state: ParseState<StringReader>): String? {
            val input = state.input()
            input.skipWhitespace()
            val fullString = input.string
            val start = input.cursor
            var pos = start

            while(pos < fullString.length && isAllowedInVarName(fullString[pos], pos == start))
                pos++

            if(pos == start)
                return null
            if(pos < fullString.length && fullString[pos] == ':')
                return null // Should be interpreted as an id instead

            input.cursor = pos
            val varName = fullString.substring(start, pos)

            // Analyze variable name
            val analyzingResultArg = PackratParserAdditionalArgs.analyzingResult.getOrNull()
            if(analyzingVariables != null && analyzingResultArg != null && input is DirectiveStringReader<*> && input.resourceCreator is AnalyzingResourceCreator) {
                val analyzingResult = analyzingResultArg.analyzingResult
                val range = StringRange(start, input.cursor)
                analyzingResult.semanticTokens.addMultiline(range, PARAMETER, 0)

                val variable = analyzingVariables.availableVariables[varName]
                if(variable != null) {
                    val baseMappingInfo = analyzingVariables.analyzingBehavior.baseMappingInfo
                    analyzingResult.addMappedActualSyntaxNode(range, object : ActualSyntaxNode {
                        override fun getHover(cursor: Int) = CompletableFuture.completedFuture(Hover(
                            listOf(org.eclipse.lsp4j.jsonrpc.messages.Either.forLeft(variable.toString())),
                                analyzingResult.toFileRange(range)
                        ))

                        override fun getDefinition(cursor: Int): CompletableFuture<ActualSyntaxNode.Definition>? {
                            @Suppress("UNCHECKED_CAST")
                            val targetRange = analyzingVariables.analyzingBehavior.tryGetNodeRange(variable as T) ?: return null
                            val absoluteRange = baseMappingInfo.cursorMapper.mapToSource(targetRange + baseMappingInfo.readSkippingChars)
                            return CompletableFuture.completedFuture(ActualSyntaxNode.Definition(org.eclipse.lsp4j.jsonrpc.messages.Either.forLeft(
                                listOf(Location(
                                    input.resourceCreator.sourceFunctionUri,
                                    Range(AnalyzingResult.getPositionFromCursor(absoluteRange.start, baseMappingInfo), AnalyzingResult.getPositionFromCursor(absoluteRange.start, baseMappingInfo))
                                ))), false)
                            )
                        }
                    })
                }
            }

            return varName
        }
    }

    class NumberProviderReferenceRule<TNumberProvider : Any>(
        private val idParser: NamedRule<StringReader, Identifier>,
        private val context: RegistryOps<*>,
        private val registryId: ResourceKey<Registry<TNumberProvider>>,
    ) : Rule<StringReader, Holder<TNumberProvider>>, ResourceSuggestion {

        private val notFoundException = DynamicCommandExceptionType { Component.literal("Failed to get element $it from registry ${registryId.identifier()}") }
        private val invalidIdError = DelayedException.create(Identifier.ERROR_INVALID)

        private val packContentFileType = PackContentFileType.getOrCreateTypeForDynamicRegistry(registryId) //TODO: Analyze id

        override fun parse(state: ParseState<StringReader>): Holder<TNumberProvider>? {
            state.input().skipWhitespace()
            val start = state.mark()
            val id = state.parse(idParser)
            if(id != null) {
                val result = context.getter(registryId).flatMap {
                    it.get(ResourceKey.create(registryId, id))
                }.getOrNull()

                IdArgumentTypeAnalyzer.analyzePackrat(id, start, state.input(), packContentFileType)

                if(result == null) {
                    // Error at the end of the id, so it's prioritized
                    state.errorCollector().store(state.mark(), this, DelayedException.create(notFoundException, id.toString()))
                    state.restore(start) // This also stores the furthest analyzing result
                }
                return result
            } else {
                state.errorCollector().store(start, this, invalidIdError)
                return null
            }
        }

        override fun possibleResources(): Stream<Identifier> {
            val lookup = ExtraDecoderBehavior.getCurrentBehavior(context)?.registries?.lookup(registryId)?.getOrNull()
                ?: return Stream.empty()
            return lookup.listElementIds().map { it.identifier() }
        }
    }

    data class AnalyzingVariableData<T: Any>(val availableVariables: Map<String, Any>, val analyzingBehavior: ExtraDecoderBehavior.NodeAnalyzingBehavior<T>)
}