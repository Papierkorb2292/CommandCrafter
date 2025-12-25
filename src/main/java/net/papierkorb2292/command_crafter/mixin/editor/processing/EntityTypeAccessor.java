package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityType.class)
public interface EntityTypeAccessor<T extends Entity> {
    @Accessor
    EntityType.EntityFactory<T> getFactory();
}
