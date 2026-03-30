package net.papierkorb2292.command_crafter.editor.processing.string_range_tree

fun interface StringEscaper {
    companion object {
        fun StringEscaper.andThen(other: StringEscaper) =
            if(other == Identity) this
            else if(this == Identity) other
            else StringEscaper { string -> other.escape(this@andThen.escape(string)) }

        fun escapeForQuotes(quotes: String) =
            StringEscaper { string -> string.replace("\\", "\\\\").replace(quotes, "\\$quotes") }
    }
    object Identity : StringEscaper {
        override fun escape(string: String) = string
    }
    fun escape(string: String): String
}