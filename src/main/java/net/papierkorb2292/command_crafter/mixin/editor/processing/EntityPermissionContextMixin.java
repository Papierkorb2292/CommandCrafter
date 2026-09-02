package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.fabric.impl.permission.EntityPermissionContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityPermissionContext.class)
public class EntityPermissionContextMixin {
    @WrapOperation(
            method = "<init>",
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lnet/minecraft/world/level/Level;getServer()Lnet/minecraft/server/MinecraftServer;"
            )
    )
    private MinecraftServer command_crafter$allowNullLevelForDummyPlayer(Level instance, Operation<MinecraftServer> original) {
        // Fabric expects the level to not be null, but it has to be null for dummy players.
        // If this happens more often, maybe consider a mixin transformer for all level() calls? :/
        if(instance == null)
            return null;
        return original.call(instance);
    }
}
