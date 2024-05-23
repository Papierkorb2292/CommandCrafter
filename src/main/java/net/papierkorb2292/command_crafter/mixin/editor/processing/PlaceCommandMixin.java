package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.server.command.PlaceCommand;
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackContentFileTypeContainer;
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
                    target = "Lnet/minecraft/server/command/CommandManager;argument(Ljava/lang/String;Lcom/mojang/brigadier/arguments/ArgumentType;)Lcom/mojang/brigadier/builder/RequiredArgumentBuilder;",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=structure"
                    )
            )
    )
    private static ArgumentType<?> command_crafter$analyzeStructureIdArgument(ArgumentType<?> argumentType) {
        ((PackContentFileTypeContainer)argumentType).command_crafter$setPackContentFileType(PackContentFileType.WORLDGEN_STRUCTURE_FILE_TYPE);
        return argumentType;
    }

    @ModifyArg(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/command/CommandManager;argument(Ljava/lang/String;Lcom/mojang/brigadier/arguments/ArgumentType;)Lcom/mojang/brigadier/builder/RequiredArgumentBuilder;",
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
        ((PackContentFileTypeContainer)argumentType).command_crafter$setPackContentFileType(PackContentFileType.STRUCTURES_FILE_TYPE);
        return argumentType;
    }

    @ModifyArg(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/command/CommandManager;argument(Ljava/lang/String;Lcom/mojang/brigadier/arguments/ArgumentType;)Lcom/mojang/brigadier/builder/RequiredArgumentBuilder;",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=feature"
                    )
            )
    )
    private static ArgumentType<?> command_crafter$analyzeFeatureIdArgument(ArgumentType<?> argumentType) {
        ((PackContentFileTypeContainer)argumentType).command_crafter$setPackContentFileType(PackContentFileType.WORLDGEN_CONFIGURED_FEATURE_FILE_TYPE);
        return argumentType;
    }

    @ModifyArg(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/command/CommandManager;argument(Ljava/lang/String;Lcom/mojang/brigadier/arguments/ArgumentType;)Lcom/mojang/brigadier/builder/RequiredArgumentBuilder;",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=jigsaw"
                    )
            )
    )
    private static ArgumentType<?> command_crafter$analyzeJigsawIdArgument(ArgumentType<?> argumentType) {
        ((PackContentFileTypeContainer)argumentType).command_crafter$setPackContentFileType(PackContentFileType.WORLDGEN_TEMPLATE_POOL_FILE_TYPE);
        return argumentType;
    }
}
