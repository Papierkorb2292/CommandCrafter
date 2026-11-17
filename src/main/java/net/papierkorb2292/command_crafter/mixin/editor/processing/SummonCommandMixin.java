package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.commands.SummonCommand;
import net.papierkorb2292.command_crafter.editor.processing.AdditionalArgumentTypeData;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingCommandTree;
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.DataObjectDecoding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SummonCommand.class)
public class SummonCommandMixin {

    @ModifyExpressionValue(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/arguments/CompoundTagArgument;compoundTag()Lcom/mojang/brigadier/arguments/ArgumentType;"
            )
    )
    private static ArgumentType<CompoundTag> command_crafter$addNbtDecoder(ArgumentType<CompoundTag> original) {
        if(AnalyzingCommandTree.Companion.getBUILDER().isBound()) {
            var builder = AnalyzingCommandTree.Companion.getBUILDER().get();
            builder.addData(original, AdditionalArgumentTypeData.INSTANCE.getDataObjectSource(), new DataObjectDecoding.DataObjectSource(DataObjectDecoding.DataObjectSourceKind.ENTITY_SUMMON, "entity"));
        }
        return original;
    }
}
