package net.papierkorb2292.command_crafter.mixin.client.game;

import net.minecraft.client.Minecraft;
import net.papierkorb2292.command_crafter.client.ClientCommandCrafter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(
            method = "runTick",
            at = @At("HEAD")
    )
    private void command_crafter$resetHoveredItem(boolean tick, CallbackInfo ci) {
        ClientCommandCrafter.INSTANCE.setCurrentlyHoveredItem(null);
    }
}
