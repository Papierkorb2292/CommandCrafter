package net.papierkorb2292.command_crafter.editor.processing.string_range_tree

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.resources.Identifier

interface StringEscaper {
    val typeId: Identifier
    fun escape(string: String): String

    companion object {
        val IDENTITY_TYPE = Identifier.withDefaultNamespace("identity")
        val IDENTITY_CODEC = MapCodec.unit(Identity)
        val QUOTE_TYPE = Identifier.withDefaultNamespace("quote")
        val QUOTE_CODEC = Codec.STRING.fieldOf("quote").xmap(::QuoteEscaper, QuoteEscaper::quotes)

        val CODECS_BY_ID = mapOf(
            IDENTITY_TYPE to IDENTITY_CODEC,
            QUOTE_TYPE to QUOTE_CODEC,
        )

        val CODEC = Identifier.CODEC.dispatch(StringEscaper::typeId, CODECS_BY_ID::get)
        val PACKET_CODEC = ByteBufCodecs.fromCodec(CODEC)

        fun escapeForQuotes(quotes: String) = QuoteEscaper(quotes)
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
}