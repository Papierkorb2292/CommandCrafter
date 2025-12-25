package net.papierkorb2292.command_crafter.mixin.parser;

import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Commands.class)
public interface CommandsAccessor {
    @Accessor
    static ClientboundCommandsPacket.NodeInspector<CommandSourceStack> getCOMMAND_NODE_INSPECTOR() {
        throw new AssertionError();
    }
}
