package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.nbt.ShortTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ShortTag.class)
public interface ShortTagAccessor {
    @Invoker("<init>")
    static ShortTag callInit(short value) {
        throw new AssertionError();
    }
}
