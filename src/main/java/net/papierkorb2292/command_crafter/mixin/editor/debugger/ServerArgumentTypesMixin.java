package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.papierkorb2292.command_crafter.parser.helper.UtilKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

// Create compatibility with adventure-platform-mod
// The mod expects a player entity in CommandManager.makeTreeForSource, which CommandCrafter
// does not provide. This mixin adds a null check when syncing the tree to fix the issue.
@Pseudo
@Mixin(targets = "net.kyori.adventure.platform.fabric.impl.ServerArgumentTypes")
public class ServerArgumentTypesMixin {
    @Inject(
            method = "knownArgumentTypes(Lnet/minecraft/class_3222;)Ljava/util/Set;",
            at = @At("HEAD"),
            remap = false,
            cancellable = true
    )
    private static void command_crafter$checkForNullWhenSyncingCommandCrafterTree(ServerPlayerEntity player, CallbackInfoReturnable<Set<Identifier>> cir) {
        var isBuildingTree = getOrNull(UtilKt.getIS_BUILDING_CLIENTSIDE_COMMAND_TREE());
        if(player == null && isBuildingTree != null && isBuildingTree) {
            cir.setReturnValue(Set.of());
        }
    }
}
