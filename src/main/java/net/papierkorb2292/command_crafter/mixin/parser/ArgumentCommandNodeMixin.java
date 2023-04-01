package net.papierkorb2292.command_crafter.mixin.parser;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_crafter.parser.helper.ServerSourceAware;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ArgumentCommandNode.class)
public class ArgumentCommandNodeMixin implements ServerSourceAware {

    @Shadow(remap = false) @Final private ArgumentType<?> type;

    public void command_crafter$setServerCommandSource(@NotNull ServerCommandSource serverCommandSource) {
        if(type instanceof ServerSourceAware serverSourceAware) {
            serverSourceAware.command_crafter$setServerCommandSource(serverCommandSource);
        }
    }
}
