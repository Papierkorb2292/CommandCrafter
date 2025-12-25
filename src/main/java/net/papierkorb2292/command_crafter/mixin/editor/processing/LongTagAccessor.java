package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.nbt.LongTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LongTag.class)
public interface LongTagAccessor {
    @Invoker("<init>")
    static LongTag callInit(long value) {
        throw new AssertionError();
    }
}
