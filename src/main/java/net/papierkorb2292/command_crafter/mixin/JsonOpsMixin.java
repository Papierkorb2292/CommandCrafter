package net.papierkorb2292.command_crafter.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.JsonOps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(JsonOps.class)
public class JsonOpsMixin {

    @WrapOperation(
            method = "convertTo(Lcom/mojang/serialization/DynamicOps;Lcom/google/gson/JsonElement;)Ljava/lang/Object;",
            at = @At(
                    value = "CONSTANT",
                    args = "classValue=com.google.gson.JsonNull"
            ),
            remap = false
    )
    private boolean checkForNullInputInConvertTo(Object object, Operation<Boolean> op) {
        return op.call(object) || object == null;
    }
}
