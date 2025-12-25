package net.papierkorb2292.command_crafter.mixin.editor;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.Commands;
import net.papierkorb2292.command_crafter.CommandCrafter;
import net.papierkorb2292.command_crafter.parser.helper.UtilKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(Commands.class)
public class CommandsMixin {

    @WrapOperation(
            method = "fillUsableCommands",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/tree/CommandNode;canUse(Ljava/lang/Object;)Z",
                    remap = false
            )
    )
    private static <S> boolean command_crafter$catchCanUseErrorWhenBuildingClientsideTree(CommandNode<S> instance, S source, Operation<Boolean> op) {
        var isBuildingClientsideTree = getOrNull(UtilKt.getIS_BUILDING_CLIENTSIDE_COMMAND_TREE());
        if(isBuildingClientsideTree == null || !isBuildingClientsideTree)
            return op.call(instance, source);
        try {
            return op.call(instance, source);
        } catch (Exception e) {
            CommandCrafter.INSTANCE.getLOGGER().debug("Caught error while building clientside command tree", e);
            return true;
        }
    }
}
