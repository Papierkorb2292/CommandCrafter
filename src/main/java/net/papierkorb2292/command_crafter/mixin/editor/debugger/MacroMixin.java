package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.server.command.AbstractServerCommandSource;
import net.minecraft.server.function.Macro;
import net.minecraft.server.function.Procedure;
import net.papierkorb2292.command_crafter.editor.debugger.helper.MacroValuesContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(Macro.class)
public class MacroMixin<T extends AbstractServerCommandSource<T>> {

    @ModifyReturnValue(
            method = "withMacroReplaced(Ljava/util/List;Ljava/util/List;Lcom/mojang/brigadier/CommandDispatcher;)Lnet/minecraft/server/function/Procedure;",
            at = @At("RETURN")
    )
    private Procedure<T> command_crafter$setProcedureMacroValues(Procedure<T> procedure, List<String> macroNames, List<String> macroValues) {
        if (procedure instanceof MacroValuesContainer container) {
            container.command_crafter$setMacroNames(macroNames);
            container.command_crafter$setMacroValues(macroValues);
        }
        return procedure;
    }
}
