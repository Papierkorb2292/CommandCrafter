package net.papierkorb2292.command_crafter.parser.helper

import com.mojang.datafixers.util.Either

interface UnparsedArgumentContainer {
    fun `command_crafter$getUnparsedArgument`(): MutableList<Either<String, RawResource>>?
    fun `command_crafter$setUnparsedArgument`(arg: MutableList<Either<String, RawResource>>?)
}