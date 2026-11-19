package net.papierkorb2292.command_crafter.mixin.parser;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandParser;
import net.minecraft.server.ServerFunctionLibrary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerFunctionLibrary.class)
public interface ServerFunctionLibraryAccessor {

    @Accessor
    CommandParser.Provider<CommandSourceStack> getParserProvider();
}
