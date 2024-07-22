package net.papierkorb2292.command_crafter.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.nbt.visitor.NbtTextFormatter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NbtTextFormatter.class)
public class NbtTextFormatterMixin {

    @Shadow private int depth;

    @ModifyExpressionValue(
            method = {
                    "visitByteArray",
                    "visitIntArray",
                    "visitLongArray",
                    "visitList"
            },
            at = @At(
                    value = "CONSTANT",
                    args = "intValue=128"
            )
    )
    private int command_crafter$deactivateEllipsisShortening(int value) {
        return Integer.MAX_VALUE;
    }

    @ModifyExpressionValue(
            method = {
                    "visitList",
                    "visitCompound"
            },
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/nbt/visitor/NbtTextFormatter;depth:I"
            )
    )
    private int command_crafter$deactivateEllipsisForDepth(int value) {
        if(depth > 64)
            return 64;
        return value;
    }
}
