package net.papierkorb2292.command_crafter.mixin.client.game;

import net.minecraft.client.MinecraftClient;
import net.papierkorb2292.command_crafter.client.ClientCommandCrafter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Inject(
            method = "render",
            at = @At("HEAD")
    )
    private void command_crafter$resetHoveredItem(boolean tick, CallbackInfo ci) {
        ClientCommandCrafter.INSTANCE.setCurrentlyHoveredItem(null);
    }
}
