package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.server.command.LootCommand;
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackContentFileTypeContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(LootCommand.class)
public class LootCommandMixin {

    @ModifyArg(
            method = "method_13203",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/command/CommandManager;argument(Ljava/lang/String;Lcom/mojang/brigadier/arguments/ArgumentType;)Lcom/mojang/brigadier/builder/RequiredArgumentBuilder;",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=loot_table",
                            ordinal = 0
                    )
            )
    )
    private static ArgumentType<?> command_crafter$analyzeFishLootTableArgument(ArgumentType<?> argumentType) {
        ((PackContentFileTypeContainer)argumentType).command_crafter$setPackContentFileType(PackContentFileType.LOOT_TABLES_FILE_TYPE);
        return argumentType;
    }

    @ModifyArg(
            method = "method_13203",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/command/CommandManager;argument(Ljava/lang/String;Lcom/mojang/brigadier/arguments/ArgumentType;)Lcom/mojang/brigadier/builder/RequiredArgumentBuilder;",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=loot_table",
                            ordinal = 1
                    )
            )
    )
    private static ArgumentType<?> command_crafter$analyzeLootLootTableArgument(ArgumentType<?> argumentType) {
        ((PackContentFileTypeContainer)argumentType).command_crafter$setPackContentFileType(PackContentFileType.LOOT_TABLES_FILE_TYPE);
        return argumentType;
    }
}
