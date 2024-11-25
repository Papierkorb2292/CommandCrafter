package net.papierkorb2292.command_crafter.mixin.editor.scoreboardStorageViewer;

import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.text.Text;
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.ServerScoreboardStorageFileSystem;
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.FileChangeType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScoreboardObjective.class)
public class ScoreboardObjectiveMixin {
    @Shadow @Final private Scoreboard scoreboard;

    @Shadow @Final private String name;

    @Inject(
            method = "setDisplayAutoUpdate",
            at = @At("HEAD")
    )
    private void command_crafter$notifyFileSystemOfSetDisplayAutoUpdate(boolean displayAutoUpdate, CallbackInfo ci) {
        if(!(scoreboard instanceof ServerScoreboard)) return;
        ServerScoreboardStorageFileSystem.Companion.onFileUpdate(
                ServerScoreboardStorageFileSystem.Directory.SCOREBOARDS,
                name,
                FileChangeType.Changed
        );
    }

    @Inject(
            method = "setDisplayName",
            at = @At("HEAD")
    )
    private void command_crafter$notifyFileSystemOfSetDisplayName(Text name, CallbackInfo ci) {
        if(!(scoreboard instanceof ServerScoreboard)) return;
        ServerScoreboardStorageFileSystem.Companion.onFileUpdate(
                ServerScoreboardStorageFileSystem.Directory.SCOREBOARDS,
                this.name,
                FileChangeType.Changed
        );
    }

    @Inject(
            method = "setRenderType",
            at = @At("HEAD")
    )
    private void command_crafter$notifyFileSystemOfSetRenderType(ScoreboardCriterion.RenderType renderType, CallbackInfo ci) {
        if(!(scoreboard instanceof ServerScoreboard)) return;
        ServerScoreboardStorageFileSystem.Companion.onFileUpdate(
                ServerScoreboardStorageFileSystem.Directory.SCOREBOARDS,
                name,
                FileChangeType.Changed
        );
    }

    @Inject(
            method = "setNumberFormat",
            at = @At("HEAD")
    )
    private void command_crafter$notifyFileSystemOfSetNumberFormat(NumberFormat numberFormat, CallbackInfo ci) {
        if(!(scoreboard instanceof ServerScoreboard)) return;
        ServerScoreboardStorageFileSystem.Companion.onFileUpdate(
                ServerScoreboardStorageFileSystem.Directory.SCOREBOARDS,
                name,
                FileChangeType.Changed
        );
    }
}
