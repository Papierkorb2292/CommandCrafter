package net.papierkorb2292.command_crafter.mixin.editor.scoreboardStorageViewer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.loot.context.LootContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net/minecraft/loot/provider/nbt/ContextLootNbtProvider$2")
public class ContextLootNbtProviderEntityTargetMixin {
    @WrapOperation(
            method = "getName",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/loot/context/LootContext$EntityTarget;name()Ljava/lang/String;"
            )
    )
    private String command_crafter$fixTargetSerialization(LootContext.EntityTarget instance, Operation<String> wrong) {
        // Minecraft uses .name when it should be using .asString, because that way it actually returns the `type`
        // value which the decoder matches against
        return instance.asString();
    }
}
