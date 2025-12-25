package net.papierkorb2292.command_crafter.mixin.client.editor;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor
    static Map<String, KeyMapping> getALL() {
        throw new AssertionError();
    }
}
