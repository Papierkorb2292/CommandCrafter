package net.papierkorb2292.command_crafter.mixin;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedArgument;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(CommandContext.class)
public interface CommandContextAccessor {
    @Accessor(remap = false)
    Map<String, ParsedArgument<CommandSourceStack, ?>> getArguments();
}
