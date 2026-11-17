package net.papierkorb2292.command_crafter.mixin.client.editor;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.flag.FeatureFlagSet;
import net.papierkorb2292.command_crafter.client.editor.processing.AnalyzingClientSuggestionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientSuggestionContext.class)
public class ClientSuggestionContextMixin {

    @WrapOperation(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;registryAccess()Lnet/minecraft/core/RegistryAccess$Frozen;"
            )
    )
    private RegistryAccess.Frozen command_crafter$allowNullConnectionForRegistryAccess(ClientPacketListener instance, Operation<RegistryAccess.Frozen> op) {
        if(instance == null && AnalyzingClientSuggestionContext.Companion.getCLIENT_SUGGESTION_CONTEXT_REGISTRY_OVERRIDE().isBound())
            return null;
        return op.call(instance);
    }

    @WrapOperation(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;enabledFeatures()Lnet/minecraft/world/flag/FeatureFlagSet;"
            )
    )
    private FeatureFlagSet command_crafter$allowNullConnectionForEnabledFeatures(ClientPacketListener instance, Operation<FeatureFlagSet> op) {
        if(instance == null && AnalyzingClientSuggestionContext.Companion.getCLIENT_SUGGESTION_CONTEXT_REGISTRY_OVERRIDE().isBound())
            return FeatureFlagSet.of();
        return op.call(instance);
    }

    @WrapOperation(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/RegistryAccess$Frozen;filterFeatures(Lnet/minecraft/world/flag/FeatureFlagSet;)Lnet/minecraft/core/HolderLookup$Provider;"
            )
    )
    private HolderLookup.Provider command_crafter$applyRegistryOverride(RegistryAccess.Frozen instance, FeatureFlagSet featureFlagSet, Operation<HolderLookup.Provider> op) {
        if(AnalyzingClientSuggestionContext.Companion.getCLIENT_SUGGESTION_CONTEXT_REGISTRY_OVERRIDE().isBound())
            return AnalyzingClientSuggestionContext.Companion.getCLIENT_SUGGESTION_CONTEXT_REGISTRY_OVERRIDE().get();
        return op.call(instance, featureFlagSet);
    }
}
