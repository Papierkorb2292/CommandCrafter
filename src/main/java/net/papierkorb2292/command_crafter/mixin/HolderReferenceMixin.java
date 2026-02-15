package net.papierkorb2292.command_crafter.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceKey;
import net.papierkorb2292.command_crafter.editor.processing.DataObjectDecoding;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(Holder.Reference.class)
public class HolderReferenceMixin<T> {

    @Shadow
    private ResourceKey<T> key;

    @Shadow
    private DataComponentMap components;

    @ModifyExpressionValue(
            method = {
                    "areComponentsBound",
                    "components"
            },
            at = @At(
                    value = "FIELD",
                    target = "components",
                    opcode = Opcodes.GETFIELD
            )
    )
    private DataComponentMap command_crafter$applyBuiltinRegistryOverride(DataComponentMap original) {
        final var registryOverride = getOrNull(DataObjectDecoding.Companion.getBUILTIN_REGISTRY_OVERRIDE());
        if(registryOverride == null)
            return original;
        final var overrideReference = registryOverride.get(key).orElse(null);
        if(overrideReference != null)
            return ((HolderReferenceMixin<?>)(Object)overrideReference).components;
        return original;
    }
}
