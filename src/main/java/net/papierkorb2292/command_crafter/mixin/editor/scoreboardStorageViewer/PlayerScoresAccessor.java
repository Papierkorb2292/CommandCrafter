package net.papierkorb2292.command_crafter.mixin.editor.scoreboardStorageViewer;

import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Score;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(targets = "net.minecraft.world.scores.PlayerScores")
public interface PlayerScoresAccessor {
    @Invoker
    Map<Objective, Score> callListRawScores();
}
