package net.papierkorb2292.command_crafter.mixin.parser;

import net.minecraft.network.packet.s2c.play.CommandTreeS2CPacket;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CommandManager.class)
public interface CommandManagerAccessor {
    @Accessor
    static CommandTreeS2CPacket.class_11409<ServerCommandSource> getField_60672() {
        throw new AssertionError();
    }
}
