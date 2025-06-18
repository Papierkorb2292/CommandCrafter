package net.papierkorb2292.command_crafter.mixin.editor;

import net.minecraft.client.network.ClientCommandSource;
import net.minecraft.command.PermissionLevelSource;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Predicate;


@Debug(export = true)
@Mixin(targets = "net/minecraft/client/network/ClientPlayNetworkHandler$1")
public class ClientPlayNetworkHandlerNodeFactoryMixin {
    @ModifyArg(
            method = "modifyNode",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/builder/ArgumentBuilder;requires(Ljava/util/function/Predicate;)Lcom/mojang/brigadier/builder/ArgumentBuilder;"
            ),
            remap = false
    )
    private Predicate<PermissionLevelSource> command_crafter$allowAnySourceClassForCheckingElevatedPrivileges(Predicate<ClientCommandSource> predicate) {
        return PermissionLevelSource::hasElevatedPermissions;
    }
}
