package net.papierkorb2292.command_crafter.editor.processing

@Suppress("unused")
class TokenModifier(val name: String) {
    val bit = 1.shl(MODIFIERS_MUTABLE.size)
    init { MODIFIERS_MUTABLE.add(name) }

    companion object {
        private val MODIFIERS_MUTABLE = mutableListOf<String>()
        val MODIFIERS: List<String> get() = MODIFIERS_MUTABLE

        val DECLARATION = TokenModifier("declaration")
        val DEFINITION = TokenModifier("definition")
        val READONLY = TokenModifier("readonly")
        val STATIC = TokenModifier("static")
        val DEPRECATED = TokenModifier("deprecated")
        val ABSTRACT = TokenModifier("abstract")
        val ASYNC = TokenModifier("async")
        val MODIFICATION = TokenModifier("modification")
        val DOCUMENTATION = TokenModifier("documentation")
        val DEFAULT_LIBRARY = TokenModifier("defaultLibrary")
    }
}