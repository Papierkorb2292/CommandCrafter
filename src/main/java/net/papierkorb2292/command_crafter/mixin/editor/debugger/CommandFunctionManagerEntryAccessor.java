package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import net.minecraft.server.function.CommandFunction;
import net.minecraft.server.function.CommandFunctionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CommandFunctionManager.Entry.class)
public interface CommandFunctionManagerEntryAccessor {
    @Accessor
    CommandFunction.Element getElement();
    @Accessor
    int getDepth();
}
