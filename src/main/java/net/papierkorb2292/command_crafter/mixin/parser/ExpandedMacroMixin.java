package net.papierkorb2292.command_crafter.mixin.parser;

import net.minecraft.server.function.ExpandedMacro;
import net.minecraft.util.Identifier;
import net.papierkorb2292.command_crafter.editor.debugger.helper.ProcedureOriginalIdContainer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ExpandedMacro.class)
public abstract class ExpandedMacroMixin implements ProcedureOriginalIdContainer {

    @Shadow public abstract Identifier id();

    private Identifier command_crafter$originalId;

    @NotNull
    @Override
    public Identifier command_crafter$getOriginalId() {
        return command_crafter$originalId != null ? command_crafter$originalId : id();
    }

    @Override
    public void command_crafter$setOriginalId(@NotNull Identifier id) {
        command_crafter$originalId = id;
    }
}
