package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.nbt.NbtShort;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NbtShort.class)
public interface NbtShortAccessor {
    @Invoker("<init>")
    static NbtShort callInit(short value) {
        throw new AssertionError();
    }
}
