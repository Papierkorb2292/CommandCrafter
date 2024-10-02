package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtShort;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NbtDouble.class)
public interface NbtDoubleAccessor {
    @Invoker("<init>")
    static NbtDouble callInit(double value) {
        throw new AssertionError();
    }
}
