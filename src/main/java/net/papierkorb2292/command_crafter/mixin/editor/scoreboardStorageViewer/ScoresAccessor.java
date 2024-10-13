package net.papierkorb2292.command_crafter.mixin.editor.scoreboardStorageViewer;

import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardScore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(targets = "net.minecraft.scoreboard.Scores")
public interface ScoresAccessor {
    @Invoker
    Map<ScoreboardObjective, ScoreboardScore> callGetScores();
}
