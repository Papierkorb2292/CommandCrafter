package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.nbt.ByteTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ByteTag.class)
public interface ByteTagAccessor {
    @Invoker("<init>")
    static ByteTag callInit(byte value) {
        throw new AssertionError();
    }
}
