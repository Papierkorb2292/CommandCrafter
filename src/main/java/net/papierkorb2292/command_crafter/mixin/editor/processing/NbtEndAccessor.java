package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.nbt.NbtEnd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NbtEnd.class)
public interface NbtEndAccessor {

    @Invoker("<init>")
    static NbtEnd callInit() {
        throw new AssertionError();
    }
}
