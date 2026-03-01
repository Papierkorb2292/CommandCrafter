package net.papierkorb2292.command_crafter.codecmod;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Object.class)
public interface ModifyJavaFieldInterfaceTemplateMixin {
    @Definition(id = "field", field = "")
    @Expression("")
    @ModifyExpressionValue(
            method = "",
            at = @At("MIXINEXTRAS:EXPRESSION")
    )
    private Object injectionHandler(Object codec) {
        return codec;
    }
}
