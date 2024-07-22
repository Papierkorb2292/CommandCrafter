package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import net.minecraft.nbt.NbtElement;
import net.minecraft.server.function.Macro;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Macro.class)
public interface MacroAccessor {
    @Invoker
    static String callToString(NbtElement nbtElement) {
        throw new AssertionError();
    }
}
