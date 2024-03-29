package net.papierkorb2292.command_crafter.parser.helper

import net.papierkorb2292.command_crafter.parser.DirectiveStringReader

interface DirectiveStringReaderConsumer {
    fun `command_crafter$setStringReader`(reader: DirectiveStringReader<*>)
}