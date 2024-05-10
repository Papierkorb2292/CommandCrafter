package net.papierkorb2292.command_crafter.editor.processing.helper

import com.mojang.datafixers.util.Either
import net.papierkorb2292.command_crafter.parser.helper.RawResource

object PackratParserAdditionalArgs {
    val analyzingResult = ThreadLocal<AnalyzingResult>()
    val unparsedArgument = ThreadLocal<MutableList<Either<String, RawResource>>>()
}