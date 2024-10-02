package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtShort;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NbtFloat.class)
public interface NbtFloatAccessor {
    @Invoker("<init>")
    static NbtFloat callInit(float value) {
        throw new AssertionError();
    }
}
