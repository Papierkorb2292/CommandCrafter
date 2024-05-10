package net.papierkorb2292.command_crafter.mixin;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedArgument;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(CommandContext.class)
public interface CommandContextAccessor {
    @Accessor
    Map<String, ParsedArgument<ServerCommandSource, ?>> getArguments();
}
