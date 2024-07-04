package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.nbt.NbtLong;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NbtLong.class)
public interface NbtLongAccessor {
    @Invoker("<init>")
    static NbtLong callInit(long value) {
        throw new AssertionError();
    }
}
