package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.nbt.NbtByte;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NbtByte.class)
public interface NbtByteAccessor {
    @Invoker("<init>")
    static NbtByte callInit(byte value) {
        throw new AssertionError();
    }
}
