package net.papierkorb2292.command_crafter.mixin.test;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerFunctionLibrary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ServerFunctionLibrary.class)
public interface ServerFunctionLibraryAccessor {
    @Accessor
    Map<Identifier, CommandFunction<CommandSourceStack>> getFunctions();
    @Accessor
    void setFunctions(Map<Identifier, CommandFunction<CommandSourceStack>> functions);
}
