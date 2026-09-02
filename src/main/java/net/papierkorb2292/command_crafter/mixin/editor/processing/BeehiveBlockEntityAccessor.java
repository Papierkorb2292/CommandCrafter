package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(BeehiveBlockEntity.class)
public interface BeehiveBlockEntityAccessor {
    @Accessor
    static List<String> getIGNORED_BEE_TAGS() {
        throw new AssertionError();
    }
}
