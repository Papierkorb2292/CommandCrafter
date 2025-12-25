package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import kotlin.Unit;
import net.minecraft.server.commands.AdvancementCommands;
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackContentFileTypeContainer;
import net.papierkorb2292.command_crafter.parser.helper.UtilKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(AdvancementCommands.class)
public class AdvancementCommandsMixin {

    @ModifyArg(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/CommandDispatcher;register(Lcom/mojang/brigadier/builder/LiteralArgumentBuilder;)Lcom/mojang/brigadier/tree/LiteralCommandNode;",
                    remap = false
            )
    )
    private static LiteralArgumentBuilder<?> command_crafter$analyzeAdvancementArguments(LiteralArgumentBuilder<?> literalArgumentBuilder) {
        for(var node : literalArgumentBuilder.getArguments()) {
            UtilKt.visitChildrenRecursively(node, child -> {
                if(child.getName().equals("advancement") && child instanceof ArgumentCommandNode<?,?> argument) {
                    var type = argument.getType();
                    if(type instanceof PackContentFileTypeContainer packContentFileTypeContainer) {
                        packContentFileTypeContainer.command_crafter$setPackContentFileType(PackContentFileType.ADVANCEMENTS_FILE_TYPE);
                    }
                }
                return Unit.INSTANCE;
            });
        }
        return literalArgumentBuilder;
    }
}
