package net.papierkorb2292.command_crafter.parser.number_provider

import net.minecraft.core.Holder

/**
 * Represents a function that can be called in a number provider term.
 * @param name The name of the function that is used in a term to call it. The name should be unique across all functions in a provider type.
 * @param argumentCountMatcher A predicate to test whether the function can take a given amount of arguments
 * @param factory The factory that instantiates the corresponding number provider given a list of arguments.
 * Callers must ensure that the length of the list matched [argumentCountMatcher]
 */
data class NumberProviderFunction<TNumberProvider: Any>(val name: String, val argumentCountMatcher: (Int) -> Boolean, val factory: (args: List<Holder<TNumberProvider>>) -> TNumberProvider) {
    constructor(name: String, argumentCount: Int, factory: (args: List<Holder<TNumberProvider>>) -> TNumberProvider) :
            this(name, { it == argumentCount }, factory)
    constructor(name: String, factory: (args: List<Holder<TNumberProvider>>) -> TNumberProvider) :
            this(name, { true }, factory)
}