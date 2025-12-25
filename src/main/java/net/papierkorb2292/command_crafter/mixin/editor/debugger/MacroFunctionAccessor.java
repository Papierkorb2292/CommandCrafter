package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import net.minecraft.nbt.Tag;
import net.minecraft.commands.functions.MacroFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(MacroFunction.class)
public interface MacroFunctionAccessor {
    @Invoker
    static String callStringify(Tag nbtElement) {
        throw new AssertionError();
    }

    @Accessor
    List<MacroFunction.Entry<?>> getEntries();
}
