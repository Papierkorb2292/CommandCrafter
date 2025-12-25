package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.nbt.EndTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EndTag.class)
public interface EndTagAccessor {

    @Invoker("<init>")
    static EndTag callInit() {
        throw new AssertionError();
    }
}
