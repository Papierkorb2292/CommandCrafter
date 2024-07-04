package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.JsonOps;
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(JsonOps.class)
public class JsonOpsMixin {

    @WrapOperation(
            method = {
                    "lambda$getStream$19",
                    "lambda$getList$21"
            },
            at = @At(
                    value = "CONSTANT",
                    args = "classValue=com/google/gson/JsonNull"
            ),
            remap = false
    )
    private static boolean command_crafter$allowJsonNullForStringRangeTree(Object element, Operation<Boolean> op) {
        if(getOrNull(StringRangeTree.AnalyzingDynamicOps.Companion.getCURRENT_ANALYZING_OPS()) != null)
            return false;
        return op.call(element);
    }
}
