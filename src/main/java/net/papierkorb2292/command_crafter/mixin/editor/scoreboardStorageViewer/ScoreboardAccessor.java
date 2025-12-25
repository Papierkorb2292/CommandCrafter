package net.papierkorb2292.command_crafter.mixin.editor.scoreboardStorageViewer;

import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(Scoreboard.class)
public interface ScoreboardAccessor {
    @Accessor
    Reference2ObjectMap<ObjectiveCriteria, List<Objective>> getObjectivesByCriteria();
}
