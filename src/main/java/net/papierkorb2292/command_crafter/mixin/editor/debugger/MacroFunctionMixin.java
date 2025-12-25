package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.functions.MacroFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.papierkorb2292.command_crafter.editor.debugger.helper.MacroValuesContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(MacroFunction.class)
public class MacroFunctionMixin<T extends ExecutionCommandSource<T>> {

    @ModifyReturnValue(
            method = "substituteAndParse(Ljava/util/List;Ljava/util/List;Lcom/mojang/brigadier/CommandDispatcher;)Lnet/minecraft/commands/functions/InstantiatedFunction;",
            at = @At("RETURN")
    )
    private InstantiatedFunction<T> command_crafter$setProcedureMacroValues(InstantiatedFunction<T> procedure, List<String> macroNames, List<String> macroValues) {
        if (procedure instanceof MacroValuesContainer container) {
            container.command_crafter$setMacroNames(macroNames);
            container.command_crafter$setMacroValues(macroValues);
        }
        return procedure;
    }
}
