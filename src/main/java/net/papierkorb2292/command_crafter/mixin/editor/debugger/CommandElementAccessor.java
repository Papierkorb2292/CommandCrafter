package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.mojang.brigadier.ParseResults;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.function.CommandFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CommandFunction.CommandElement.class)
public interface CommandElementAccessor {
    @Accessor
    ParseResults<ServerCommandSource> getParsed();
}
