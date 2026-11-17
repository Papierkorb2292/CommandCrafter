package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.server.commands.PlaceCommand;
import net.papierkorb2292.command_crafter.editor.processing.AdditionalArgumentTypeData;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingCommandTree;
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(PlaceCommand.class)
public class PlaceCommandMixin {
    @ModifyArg(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/Commands;argument(Ljava/lang/String;Lcom/mojang/brigadier/arguments/ArgumentType;)Lcom/mojang/brigadier/builder/RequiredArgumentBuilder;",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=template"
                    )
            )
    )
    private static ArgumentType<?> command_crafter$analyzeTemplateIdArgument(ArgumentType<?> argumentType) {
        if(AnalyzingCommandTree.Companion.getBUILDER().isBound()) {
            AnalyzingCommandTree.Companion.getBUILDER().get().addData(
                    argumentType,
                    AdditionalArgumentTypeData.INSTANCE.getPackContentFileType(),
                    PackContentFileType.Companion.getSTRUCTURES_FILE_TYPE()
            );
        }
        return argumentType;
    }
}
