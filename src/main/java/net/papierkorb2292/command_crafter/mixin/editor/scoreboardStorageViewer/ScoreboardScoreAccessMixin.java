package net.papierkorb2292.command_crafter.mixin.editor.scoreboardStorageViewer;

import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Objective;
import net.minecraft.server.ServerScoreboard;
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.ServerScoreboardStorageFileSystem;
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.FileChangeType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.scores.Scoreboard$1")
public class ScoreboardScoreAccessMixin {

    @Shadow @Final
    Objective val$objective;

    @Shadow
    @Final
    Scoreboard field_47548;

    @Inject(
            method = "set",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/scores/Score;value(I)V"
            )
    )
    private void command_crafter$notifyFileSystemOfObjectiveChangeOnSetScore(int score, CallbackInfo ci) {
        if(!(field_47548 instanceof ServerScoreboard)) return;
        ServerScoreboardStorageFileSystem.Companion.onFileUpdate(
                ServerScoreboardStorageFileSystem.Directory.SCOREBOARDS,
                val$objective.getName(),
                FileChangeType.Changed
        );
    }
}
