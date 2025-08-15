package net.papierkorb2292.command_crafter.mixin.editor;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.world.GameRules;
import net.papierkorb2292.command_crafter.editor.DirectServerConnection;
import net.papierkorb2292.command_crafter.editor.NetworkServerConnectionHandler;
import net.papierkorb2292.command_crafter.networking.packets.NotifyCanReloadWorldgenS2CPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRules.Rule.class)
public class GameRulesRuleMixin<T extends GameRules.Rule<T>> {
    @Shadow @Final protected GameRules.Type<T> type;

    @Inject(
            method = "set",
            at = @At("RETURN")
    )
    private void command_crafter$notifyClientsOfCanReloadWorldgen(CommandContext<ServerCommandSource> context, String name, CallbackInfo ci) {
        if(GameRules.getRuleType(DirectServerConnection.Companion.getWORLDGEN_DEVTOOLS_RELOAD_REGISTRIES_KEY()) != type)
            return;
        //noinspection ConstantValue
        if(!((Object)this instanceof GameRules.BooleanRule booleanRule))
            return;
        final var server = context.getSource().getServer();
        if(server == null)
            return;
        for(final var player : server.getPlayerManager().getPlayerList())
            if(NetworkServerConnectionHandler.INSTANCE.isPlayerAllowedConnection(player))
                ServerPlayNetworking.send(player, new NotifyCanReloadWorldgenS2CPacket(booleanRule.get()));
    }
}
