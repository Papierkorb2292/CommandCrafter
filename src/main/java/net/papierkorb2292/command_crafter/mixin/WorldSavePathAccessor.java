package net.papierkorb2292.command_crafter.mixin;

import net.minecraft.util.WorldSavePath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(WorldSavePath.class)
public interface WorldSavePathAccessor {

    @Invoker("<init>")
    static WorldSavePath callConstructor(@SuppressWarnings("unused") String relativePath) {
        throw new AssertionError();
    }
}
