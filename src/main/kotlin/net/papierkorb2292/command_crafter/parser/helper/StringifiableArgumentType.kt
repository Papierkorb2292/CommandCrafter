package net.papierkorb2292.command_crafter.parser.helper

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.datafixers.util.Either
import net.minecraft.server.command.ServerCommandSource
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.RawZipResourceCreator

interface StringifiableArgumentType {
    @Throws(CommandSyntaxException::class)
    fun `command_crafter$stringifyArgument`(
        context: CommandContext<ServerCommandSource>,
        name: String,
        reader: DirectiveStringReader<RawZipResourceCreator>
    ): List<Either<String, RawResource>>?
}