package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.nbt.NbtString;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NbtString.class)
public interface NbtStringAccessor {
    @Invoker("<init>")
    static NbtString callInit(String value) {
        throw new AssertionError();
    }
}
