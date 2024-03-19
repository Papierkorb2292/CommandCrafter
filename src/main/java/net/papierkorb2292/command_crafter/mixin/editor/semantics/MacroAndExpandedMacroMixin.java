package net.papierkorb2292.command_crafter.mixin.editor.semantics;

import net.minecraft.server.function.ExpandedMacro;
import net.minecraft.server.function.Macro;
import net.papierkorb2292.command_crafter.editor.processing.helper.DocumentationContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin({Macro.class, ExpandedMacro.class})
public class MacroAndExpandedMacroMixin implements DocumentationContainer {

    @Nullable
    private String command_crafter$documentation;

    @Nullable
    @Override
    public String command_crafter$getDocumentation() {
        return command_crafter$documentation;
    }

    @Override
    public void command_crafter$setDocumentation(@NotNull String documentation) {
        command_crafter$documentation = documentation;
    }
}