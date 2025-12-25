package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.server.commands.SummonCommand;
import net.papierkorb2292.command_crafter.editor.processing.DataObjectDecoding;
import net.papierkorb2292.command_crafter.editor.processing.helper.DataObjectSourceContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SummonCommand.class)
public class SummonCommandMixin {

    @ModifyExpressionValue(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/arguments/CompoundTagArgument;compoundTag()Lnet/minecraft/commands/arguments/CompoundTagArgument;"
            )
    )
    private static CompoundTagArgument command_crafter$addNbtDecoder(CompoundTagArgument original) {
        ((DataObjectSourceContainer)original).command_crafter$setDataObjectSource(new DataObjectDecoding.DataObjectSource(DataObjectDecoding.DataObjectSourceKind.ENTITY_REGISTRY_ENTRY, "entity"));
        return original;
    }
}
