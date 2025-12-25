package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ShortTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FloatTag.class)
public interface FloatTagAccessor {
    @Invoker("<init>")
    static FloatTag callInit(float value) {
        throw new AssertionError();
    }
}
