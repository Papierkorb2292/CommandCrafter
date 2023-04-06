package net.papierkorb2292.command_crafter.editor.processing

@Suppress("unused")
class TokenType(val name: String) {
    val id = TYPES_MUTABLE.size
    init { TYPES_MUTABLE.add(name) }

    companion object {
        private val TYPES_MUTABLE = mutableListOf<String>()
        val TYPES: List<String> get() = TYPES_MUTABLE

        val NAMESPACE = TokenType("namespace")
        val CLASS = TokenType("class")
        val ENUM = TokenType("enum")
        val INTERFACE = TokenType("interface")
        val STRUCT = TokenType("struct")
        val TYPE_PARAMETER = TokenType("typeParameter")
        val TYPE = TokenType("type")
        val PARAMETER = TokenType("parameter")
        val VARIABLE = TokenType("variable")
        val PROPERTY = TokenType("property")
        val ENUM_MEMBER = TokenType("enumMember")
        val DECORATOR = TokenType("decorator")
        val EVENT = TokenType("event")
        val FUNCTION = TokenType("function")
        val METHOD = TokenType("method")
        val MACRO = TokenType("macro")
        val LABEL = TokenType("label")
        val COMMENT = TokenType("comment")
        val STRING = TokenType("string")
        val KEYWORD = TokenType("keyword")
        val NUMBER = TokenType("number")
        val REGEXP = TokenType("regexp")
        val OPERATOR = TokenType("operator")
    }
}