package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.server.commands.data.DataCommands;
import net.papierkorb2292.command_crafter.editor.processing.DataObjectDecoding;
import net.papierkorb2292.command_crafter.editor.processing.helper.DataObjectSourceContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DataCommands.class)
public class DataCommandsMixin {

    @ModifyExpressionValue(
            method = "lambda$register$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/arguments/CompoundTagArgument;compoundTag()Lnet/minecraft/commands/arguments/CompoundTagArgument;"
            )
    )
    private static CompoundTagArgument command_crafter$addMergeNbtDecoder(CompoundTagArgument original, DataCommands.DataProvider provider, ArgumentBuilder<?, ?> argumentBuilder) {
        if(argumentBuilder instanceof RequiredArgumentBuilder<?,?> requiredArgumentBuilder) {
            final var argumentType = requiredArgumentBuilder.getType();
            if(argumentType instanceof EntityArgument)
                ((DataObjectSourceContainer) original).command_crafter$setDataObjectSource(new DataObjectDecoding.DataObjectSource(DataObjectDecoding.DataObjectSourceKind.ENTITY_CHANGE, "target"));
            else if(argumentType instanceof BlockPosArgument)
                ((DataObjectSourceContainer) original).command_crafter$setDataObjectSource(new DataObjectDecoding.DataObjectSource(DataObjectDecoding.DataObjectSourceKind.BLOCK_ENTITY_CHANGE, "targetPos"));
        }
        return original;
    }
}
