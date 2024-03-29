package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.server.command.ExecuteCommand;
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackContentFileTypeContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(ExecuteCommand.class)
public class ExecuteCommandMixin {

    @ModifyArg(
            method = "addConditionArguments",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/command/CommandManager;argument(Ljava/lang/String;Lcom/mojang/brigadier/arguments/ArgumentType;)Lcom/mojang/brigadier/builder/RequiredArgumentBuilder;",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=predicate"
                    )
            )
    )
    private static ArgumentType<?> command_crafter$analyzePredicateArgument(ArgumentType<?> argumentType) {
        ((PackContentFileTypeContainer)argumentType).command_crafter$setPackContentFileType(PackContentFileType.PREDICATES_FILE_TYPE);
        return argumentType;
    }
}
