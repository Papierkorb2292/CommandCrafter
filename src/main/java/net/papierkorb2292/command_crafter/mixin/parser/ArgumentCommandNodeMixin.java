package net.papierkorb2292.command_crafter.mixin.parser;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import net.minecraft.command.CommandSource;
import net.papierkorb2292.command_crafter.parser.helper.SourceAware;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ArgumentCommandNode.class)
public class ArgumentCommandNodeMixin implements SourceAware {

    @Shadow(remap = false) @Final private ArgumentType<?> type;

    @Override
    public void command_crafter$setCommandSource(@NotNull CommandSource commandSource) {
        if(type instanceof SourceAware sourceAware) {
            sourceAware.command_crafter$setCommandSource(commandSource);
        }
    }
}
