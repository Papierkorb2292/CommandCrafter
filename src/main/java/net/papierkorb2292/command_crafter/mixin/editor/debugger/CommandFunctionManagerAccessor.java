package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.function.CommandFunctionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CommandFunctionManager.class)
public interface CommandFunctionManagerAccessor {
    @Accessor
    MinecraftServer getServer();
    @Accessor
    void setExecution(CommandFunctionManager.Execution execution);
}
