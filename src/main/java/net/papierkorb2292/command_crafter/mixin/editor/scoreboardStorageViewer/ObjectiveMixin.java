package net.papierkorb2292.command_crafter.mixin.editor.scoreboardStorageViewer;

import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.world.scores.Objective;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.Component;
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.ServerScoreboardStorageFileSystem;
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.FileChangeType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Objective.class)
public class ObjectiveMixin {
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
    private void command_crafter$notifyFileSystemOfSetDisplayName(Component name, CallbackInfo ci) {
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
    private void command_crafter$notifyFileSystemOfSetRenderType(ObjectiveCriteria.RenderType renderType, CallbackInfo ci) {
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
