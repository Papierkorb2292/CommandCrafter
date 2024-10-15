package net.papierkorb2292.command_crafter.mixin.editor.scoreboardStorageViewer;

import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(Scoreboard.class)
public interface ScoreboardAccessor {
    @Accessor
    Reference2ObjectMap<ScoreboardCriterion, List<ScoreboardObjective>> getObjectivesByCriterion();
}
