package net.papierkorb2292.command_crafter.mixin.editor.scoreboardStorageViewer;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.scoreboard.*;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.text.Text;
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.ServerScoreboardStorageFileSystem;
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.FileChangeType;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Scoreboard.class)
public class ScoreboardMixin {

    @Inject(
            method = "addObjective",
            at = @At("HEAD")
    )
    private void command_crafter$notifyFileSystemOfObjectiveCreation(String name, ScoreboardCriterion criterion, Text displayName, ScoreboardCriterion.RenderType renderType, boolean displayAutoUpdate, NumberFormat numberFormat, CallbackInfoReturnable<ScoreboardObjective> cir) {
        //noinspection ConstantValue
        if(!((Object)this instanceof ServerScoreboard)) return;
        ServerScoreboardStorageFileSystem.Companion.onFileUpdate(
                ServerScoreboardStorageFileSystem.Directory.SCOREBOARDS,
                name,
                FileChangeType.Created
        );
    }

    @Inject(
            method = "removeObjective",
            at = @At("HEAD")
    )
    private void command_crafter$notifyFileSystemOfObjectiveDeletion(ScoreboardObjective objective, CallbackInfo ci) {
        //noinspection ConstantValue
        if(!((Object)this instanceof ServerScoreboard)) return;
        ServerScoreboardStorageFileSystem.Companion.onFileUpdate(
                ServerScoreboardStorageFileSystem.Directory.SCOREBOARDS,
                objective.getName(),
                FileChangeType.Deleted
        );
    }

    @ModifyExpressionValue(
            method = "removeScores",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;"
            )
    )
    private Object command_crafter$notifyFileSystemOfObjectiveChangeOnEntityRemoved(Object scores) {
        //noinspection ConstantValue
        if(!((Object)this instanceof ServerScoreboard) || scores == null) return scores;
        for(final var objective : ((ScoresAccessor)scores).callGetScores().keySet()) {
            ServerScoreboardStorageFileSystem.Companion.onFileUpdate(
                    ServerScoreboardStorageFileSystem.Directory.SCOREBOARDS,
                    objective.getName(),
                    FileChangeType.Changed
            );
        }
        return scores;
    }

    @ModifyExpressionValue(
            method = "removeScore",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/scoreboard/Scores;remove(Lnet/minecraft/scoreboard/ScoreboardObjective;)Z"
            )
    )
    private boolean command_crafter$notifyFileSystemOfObjectiveChangeOnScoreRemoved(boolean wasRemoved, ScoreHolder scoreHolder, ScoreboardObjective objective) {
        //noinspection ConstantValue
        if(!(((Object)this instanceof ServerScoreboard) && wasRemoved)) return wasRemoved;
        ServerScoreboardStorageFileSystem.Companion.onFileUpdate(
                ServerScoreboardStorageFileSystem.Directory.SCOREBOARDS,
                objective.getName(),
                FileChangeType.Changed
        );
        return wasRemoved;
    }

    @ModifyVariable(
            method = "getOrCreateScore(Lnet/minecraft/scoreboard/ScoreHolder;Lnet/minecraft/scoreboard/ScoreboardObjective;Z)Lnet/minecraft/scoreboard/ScoreAccess;",
            at = @At("RETURN")
    )
    private MutableBoolean command_crafter$notifyFileSystemOfObjectiveChangeOnScoreCreation(MutableBoolean createdScore, ScoreHolder scoreHolder, ScoreboardObjective objective) {
        //noinspection ConstantValue
        if(!(((Object)this instanceof ServerScoreboard) && createdScore.isTrue())) return createdScore;
        ServerScoreboardStorageFileSystem.Companion.onFileUpdate(
                ServerScoreboardStorageFileSystem.Directory.SCOREBOARDS,
                objective.getName(),
                FileChangeType.Changed
        );
        return createdScore;
    }
}
