package net.papierkorb2292.command_crafter.parser.number_provider

import net.minecraft.core.Holder

/**
 * Represents an infix that can be used in a number provider term. An infix differs from a function in that its
 * name is always a single character, and it always takes exactly two arguments. Due to being an infix, it also has
 * to define a precedence level that defines the binding order.
 * @param name The name of this infix that is used in a term to call it. The name should be unique across all infixes in a provider type.
 * @param precedenceLevel The precedence level of this infix. Smaller precedence levels bind stronger (for example multiplication has a smaller precedence level than addition). Values should correspond to the table at https://en.wikipedia.org/wiki/Order_of_operations#Programming_languages and must be non-negative
 * @param factory The factory that instantiates the corresponding number provider given the left and right arguments
 */
data class NumberProviderInfix<TNumberProvider: Any>(val name: Char, val precedenceLevel: Int, val factory: (left: Holder<TNumberProvider>, right: Holder<TNumberProvider>) -> TNumberProvider) {
    init {
        require(precedenceLevel >= 0) { "Precedence level must not be negative" }
    }
}