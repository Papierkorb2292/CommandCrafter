package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import net.minecraft.server.function.ExpandedMacro;
import net.papierkorb2292.command_crafter.editor.debugger.helper.MacroValuesContainer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

@Mixin(ExpandedMacro.class)
public class ExpandedMacroMixin implements MacroValuesContainer {

    private List<String> command_crafter$macroNames;
    private List<String> command_crafter$macroValues;

    @Override
    public void command_crafter$setMacroNames(@NotNull List<String> macroNames) {
        this.command_crafter$macroNames = macroNames;
    }

    @Override
    public void command_crafter$setMacroValues(@NotNull List<String> macroValues) {
        this.command_crafter$macroValues = macroValues;
    }

    @Override
    public List<String> command_crafter$getMacroNames() {
        return this.command_crafter$macroNames;
    }

    @Override
    public List<String> command_crafter$getMacroValues() {
        return this.command_crafter$macroValues;
    }
}
