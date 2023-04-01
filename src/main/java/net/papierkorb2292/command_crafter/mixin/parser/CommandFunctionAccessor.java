package net.papierkorb2292.command_crafter.mixin.parser;

import net.minecraft.server.function.CommandFunction;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CommandFunction.class)
public interface CommandFunctionAccessor {

    @Mutable @Accessor
    void setId(Identifier id);
}
