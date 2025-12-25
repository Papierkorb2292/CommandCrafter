package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.nbt.StringTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(StringTag.class)
public interface StringTagAccessor {
    @Invoker("<init>")
    static StringTag callInit(String value) {
        throw new AssertionError();
    }
}
