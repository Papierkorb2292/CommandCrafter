package net.papierkorb2292.command_crafter.parser.number_provider

import net.minecraft.core.Holder

/**
 * Represents a function that can be called in a number provider term.
 * @param name The name of the function that is used in a term to call it. The name should be unique across all functions in a provider type.
 * @param argumentCountMatcher A predicate to test whether the function can take a given amount of arguments
 * @param determined True if the number provider's output always stays the same when no inputs change
 * @param factory The factory that instantiates the corresponding number provider given a list of arguments.
 * Callers must ensure that the length of the list matched [argumentCountMatcher]
 */
data class NumberProviderFunction<TNumberProvider: Any>(val name: String, val argumentCountMatcher: (Int) -> Boolean, val determined: Boolean, val factory: (args: List<Holder<TNumberProvider>>) -> TNumberProvider) {
    constructor(name: String, argumentCount: Int, determined: Boolean, factory: (args: List<Holder<TNumberProvider>>) -> TNumberProvider) :
            this(name, { it == argumentCount }, determined, factory)
    constructor(name: String, determined: Boolean, factory: (args: List<Holder<TNumberProvider>>) -> TNumberProvider) :
            this(name, { true }, determined, factory)
}