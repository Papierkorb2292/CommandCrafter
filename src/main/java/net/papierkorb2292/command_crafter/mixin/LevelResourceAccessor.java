package net.papierkorb2292.command_crafter.mixin;

import net.minecraft.world.level.storage.LevelResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LevelResource.class)
public interface LevelResourceAccessor {

    @Invoker("<init>")
    static LevelResource callConstructor(@SuppressWarnings("unused") String relativePath) {
        throw new AssertionError();
    }
}
