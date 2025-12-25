package net.papierkorb2292.command_crafter.mixin.editor.scoreboardStorageViewer;

import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Objective.class)
public interface ObjectiveAccessor {
    @Accessor @Mutable
    void setCriteria(ObjectiveCriteria criterion);
}
