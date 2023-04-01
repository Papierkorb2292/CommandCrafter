package net.papierkorb2292.command_crafter.parser

import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder
import net.minecraft.util.Identifier

class DirectiveManager {
    companion object {
        val DIRECTIVES = FabricRegistryBuilder.createSimple<(DirectiveStringReader<*>) -> Unit>(null, Identifier("command_crafter", "directives")).buildAndRegister()!!
    }

    fun readDirective(reader: DirectiveStringReader<*>) {
        val directive = reader.readUnquotedString()
        reader.expect(' ')
        (DIRECTIVES.get(Identifier(directive))
            ?: throw IllegalArgumentException("Error while parsing function: Encountered unknown directive '$directive' on line ${reader.currentLine}"))
            .invoke(reader)
    }
}
