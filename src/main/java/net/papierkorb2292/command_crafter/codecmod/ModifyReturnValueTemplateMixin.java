package net.papierkorb2292.command_crafter.codecmod;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Object.class)
public class ModifyReturnValueTemplateMixin {

    @Shadow static Object shadow;

    @ModifyReturnValue(
            method = "",
            at = @At("RETURN")
    )
    private static Object injectionHandler(Object codec) {
        return codec;
    }
}
