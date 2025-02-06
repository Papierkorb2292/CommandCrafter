package net.papierkorb2292.command_crafter.editor.processing.helper

import com.mojang.datafixers.util.Either
import com.mojang.serialization.Decoder
import com.mojang.serialization.DynamicOps
import net.minecraft.nbt.NbtElement
import net.papierkorb2292.command_crafter.parser.helper.RawResource

object PackratParserAdditionalArgs {
    val analyzingResult = ThreadLocal<AnalyzingResult>()
    val stringifiedArgument = ThreadLocal<MutableList<Either<String, RawResource>>>()
    val delayedDecodeNbtAnalyzeCallback = ThreadLocal<(DynamicOps<NbtElement>, Decoder<*>) -> Unit>()
}