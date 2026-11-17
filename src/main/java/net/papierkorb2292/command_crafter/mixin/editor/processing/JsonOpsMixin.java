package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.JsonOps;
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.TreeOperations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(JsonOps.class)
public class JsonOpsMixin {

    @WrapOperation(
            method = {
                    "getStream(Lcom/google/gson/JsonElement;)Lcom/mojang/serialization/DataResult; -> Ljava/util/function/Function;apply(Lcom/google/gson/JsonElement;)Lcom/google/gson/JsonElement;",
                    "getList(Lcom/google/gson/JsonElement;)Lcom/mojang/serialization/DataResult; -> Ljava/util/function/Consumer;accept(Ljava/util/function/Consumer;)V"
            },
            at = @At(
                    value = "CONSTANT",
                    args = "classValue=com/google/gson/JsonNull"
            )
    )
    private static boolean command_crafter$allowJsonNullForStringRangeTree(Object element, Operation<Boolean> op) {
        final var isAnalyzingDecoder = getOrNull(TreeOperations.Companion.getIS_ANALYZING_DECODER());
        if(isAnalyzingDecoder != null && isAnalyzingDecoder)
            return false;
        return op.call(element);
    }
}
