package net.papierkorb2292.command_crafter.mixin.editor.scoreboardStorageViewer;

import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ServerScoreboard;
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.ServerScoreboardStorageFileSystem;
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.FileChangeType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.scoreboard.Scoreboard$1")
public class ScoreboardScoreAccessMixin {

    @Shadow @Final
    Scoreboard field_47548;

    @Shadow @Final
    ScoreboardObjective field_47546;

    @Inject(
            method = "setScore",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/scoreboard/ScoreboardScore;setScore(I)V"
            )
    )
    private void command_crafter$notifyFileSystemOfObjectiveChangeOnSetScore(int score, CallbackInfo ci) {
        if(!(field_47548 instanceof ServerScoreboard)) return;
        ServerScoreboardStorageFileSystem.Companion.onFileUpdate(
                ServerScoreboardStorageFileSystem.Directory.SCOREBOARDS,
                field_47546.getName(),
                FileChangeType.Changed
        );
    }
}
