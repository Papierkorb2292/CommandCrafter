package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.nbt.NbtInt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NbtInt.class)
public interface NbtIntAccessor {
    @Invoker("<init>")
    static NbtInt callInit(int value) {
        throw new AssertionError();
    }
}
