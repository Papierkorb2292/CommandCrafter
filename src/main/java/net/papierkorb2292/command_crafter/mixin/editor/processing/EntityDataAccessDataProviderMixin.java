package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.google.common.collect.ImmutableSet;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.papierkorb2292.command_crafter.editor.processing.helper.IsNonPlayerSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Set;

@Mixin(targets = "net/minecraft/server/commands/data/EntityDataAccessor$1")
public class EntityDataAccessDataProviderMixin {

    private final Set<String> command_crafter$nonPlayerCommands = ImmutableSet.of("merge", "modify", "remove", "result", "success");

    @ModifyExpressionValue(
            method = "wrap",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/arguments/EntityArgument;entity()Lnet/minecraft/commands/arguments/EntityArgument;"
            )
    )
    private EntityArgument command_crafter$setIsNonPlayerSelector(EntityArgument entityArgument, ArgumentBuilder<CommandSourceStack, ?> parent) {
        if(parent instanceof LiteralArgumentBuilder<?> literal && command_crafter$nonPlayerCommands.contains(literal.getLiteral())) {
            ((IsNonPlayerSelector)entityArgument).command_crafter$setIsNonPlayerSelector(true);
        }
        return entityArgument;
    }
}
