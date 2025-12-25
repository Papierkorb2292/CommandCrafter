package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ShortTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DoubleTag.class)
public interface DoubleTagAccessor {
    @Invoker("<init>")
    static DoubleTag callInit(double value) {
        throw new AssertionError();
    }
}
