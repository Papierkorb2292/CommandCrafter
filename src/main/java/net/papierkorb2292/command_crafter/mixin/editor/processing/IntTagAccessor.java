package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.nbt.IntTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(IntTag.class)
public interface IntTagAccessor {
    @Invoker("<init>")
    static IntTag callInit(int value) {
        throw new AssertionError();
    }
}
