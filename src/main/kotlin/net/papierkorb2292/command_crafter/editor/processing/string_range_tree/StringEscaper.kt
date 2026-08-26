package net.papierkorb2292.command_crafter.editor.processing.string_range_tree

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.resources.Identifier

interface StringEscaper {
    val typeId: Identifier
    fun escape(string: String): String

    companion object {
        val CODECS_BY_ID = mutableMapOf<Identifier, MapCodec<out StringEscaper>>()
        val CODEC = Identifier.CODEC.dispatch(StringEscaper::typeId, CODECS_BY_ID::get)

        val IDENTITY_TYPE = Identifier.withDefaultNamespace("identity")
        val QUOTE_TYPE = Identifier.withDefaultNamespace("quote")
        val COMBINED_TYPE = Identifier.withDefaultNamespace("combined")

        init {
            CODECS_BY_ID[IDENTITY_TYPE] = MapCodec.unit(Identity)
            CODECS_BY_ID[QUOTE_TYPE] = Codec.STRING.fieldOf("quote").xmap(::QuoteEscaper, QuoteEscaper::quotes)
            CODECS_BY_ID[COMBINED_TYPE] = CODEC.listOf().fieldOf("escapers").xmap(::CombinedEscaper, CombinedEscaper::escapers)
        }

        val PACKET_CODEC = ByteBufCodecs.fromCodec(CODEC)

        fun escapeForQuotes(quotes: String) = QuoteEscaper(quotes)
        fun combine(escapers: List<StringEscaper>) = CombinedEscaper(escapers)
    }

    object Identity : StringEscaper {
        override val typeId: Identifier
            get() = IDENTITY_TYPE
        override fun escape(string: String) = string
    }

    class QuoteEscaper(val quotes: String) : StringEscaper {
        override val typeId: Identifier
            get() = QUOTE_TYPE
        override fun escape(string: String) =
            string.replace("\\", "\\\\").replace(quotes, "\\$quotes")
    }

    class CombinedEscaper(val escapers: List<StringEscaper>): StringEscaper {
        override val typeId: Identifier
            get() = COMBINED_TYPE

        override fun escape(string: String) =
            escapers.foldRight(string, StringEscaper::escape)
    }
}