package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.nbt.CompoundTag;
import net.papierkorb2292.command_crafter.editor.processing.command_arguments.NbtPathArgumentAnalyzer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NbtPathArgument.NbtPath.class)
public class NbtPathArgumentNbtPathMixin {
    @ModifyExpressionValue(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/codecs/PrimitiveCodec;comapFlatMap(Ljava/util/function/Function;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"
            )
    )
    private static Codec<CompoundTag> command_crafter$storeFlattenedCodecInput(Codec<CompoundTag> codec) {
        return NbtPathArgumentAnalyzer.Companion.getMalformedStringAnalyzer().wrapCodec(codec);
    }

    @ModifyExpressionValue(
            method = "lambda$static$0", // Targets comapFlatMap for CODEC
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/DataResult;success(Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;"
            )
    )
    private static DataResult<?> command_crafter$finishFlattenedCodecAnalyzingResult(DataResult<?> result, String s) {
        NbtPathArgumentAnalyzer.Companion.getMalformedStringAnalyzer().onParsed(Integer.MAX_VALUE, null);
        return result;
    }

    @ModifyExpressionValue(
            method = "lambda$static$0", // Targets comapFlatMap for CODEC
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/DataResult;error(Ljava/util/function/Supplier;)Lcom/mojang/serialization/DataResult;"
            )
    )
    private static DataResult<?> command_crafter$markFlattenedCodecSyntaxError(DataResult<?> result, String s, @Local CommandSyntaxException exception) {
        NbtPathArgumentAnalyzer.Companion.getMalformedStringAnalyzer().onParsed(exception.getCursor(), exception.getMessage());
        return result;
    }
}
