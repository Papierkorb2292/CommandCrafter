package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.network.chat.contents.NbtContents;
import net.minecraft.util.CompilableString;
import net.papierkorb2292.command_crafter.editor.processing.command_arguments.NbtPathArgumentAnalyzer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Function;

@Mixin(NbtContents.class)
public class NbtContentsMixin {
    @WrapOperation(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/CompilableString;codec(Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"
            )
    )
    private static <T> Codec<CompilableString<T>> command_crafter$analyzeEmbeddedSelector(Function<String, DataResult<T>> compiler, Operation<Codec<CompilableString<T>>> op) {
        final var parser = (CompilableString.CommandParserHelper<T>)compiler;
        final var stringAnalyzing = NbtPathArgumentAnalyzer.Companion.getMalformedStringAnalyzer();
        return stringAnalyzing.wrapCodec(op.call(stringAnalyzing.wrapCommandParserHelper(parser)));
    }
}
