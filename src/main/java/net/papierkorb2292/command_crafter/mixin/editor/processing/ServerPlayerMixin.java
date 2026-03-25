package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.network.TextFilter;
import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.papierkorb2292.command_crafter.editor.processing.DataObjectDecoding;
import net.papierkorb2292.command_crafter.helper.DummyWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin extends Avatar {

    @Shadow
    @Final
    private MinecraftServer server;

    protected ServerPlayerMixin(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;<init>(Lnet/minecraft/world/level/Level;Lcom/mojang/authlib/GameProfile;)V"
            )
    )
    private static Level command_crafter$applyLevelOverride(Level level, @Share("isDummy") LocalBooleanRef isDummy) {
        final var override = getOrNull(DataObjectDecoding.Companion.getPLAYER_CONSTRUCTOR_LEVEL_OVERRIDE());
        if (override == null) return level;
        isDummy.set(true);
        return override;
    }

    @WrapOperation(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;createTextFilterForPlayer(Lnet/minecraft/server/level/ServerPlayer;)Lnet/minecraft/server/network/TextFilter;"
            )
    )
    private static TextFilter command_crafter$allowNullServerForTextFilter(MinecraftServer server, ServerPlayer player, Operation<TextFilter> op, @Share("isDummy") LocalBooleanRef isDummy) {
        return server != null || !isDummy.get() ? op.call(server, player) : TextFilter.DUMMY;
    }

    @WrapOperation(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;createGameModeForPlayer(Lnet/minecraft/server/level/ServerPlayer;)Lnet/minecraft/server/level/ServerPlayerGameMode;"
            )
    )
    private static ServerPlayerGameMode command_crafter$allowNullServerForGameMode(MinecraftServer server, ServerPlayer player, Operation<ServerPlayerGameMode> op, @Share("isDummy") LocalBooleanRef isDummy) {
        return server != null || !isDummy.get() ? op.call(server, player) : null;
    }

    @WrapWithCondition(
            method = { "<init>", "readAdditionalSaveData" },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayerGameMode;setGameModeForPlayer(Lnet/minecraft/world/level/GameType;Lnet/minecraft/world/level/GameType;)V"
            ),
            require = 2
    )
    private static boolean command_crafter$allowNullGameMode(ServerPlayerGameMode instance, GameType gameModeForPlayer, GameType previousGameModeForPlayer) {
        return instance != null;
    }

    @WrapOperation(
            method = { "<init>", "readAdditionalSaveData" },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;calculateGameModeForNewPlayer(Lnet/minecraft/world/level/GameType;)Lnet/minecraft/world/level/GameType;"
            ),
            require = 2
    )
    private GameType command_crafter$allowNullServerForCalculateGameMode(ServerPlayer instance, GameType loadedGameType, Operation<GameType> original) {
        return server != null ? original.call(instance, loadedGameType) : null;
    }

    @WrapOperation(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;getPlayerList()Lnet/minecraft/server/players/PlayerList;"
            )
    )
    private PlayerList command_crafter$allowNullServerForPlayerList(MinecraftServer instance, Operation<PlayerList> original, @Share("isDummy") LocalBooleanRef isDummy) {
        return instance != null || !isDummy.get() ? original.call(instance) : null;
    }

    @WrapOperation(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/players/PlayerList;getPlayerStats(Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/stats/ServerStatsCounter;"
            )
    )
    private ServerStatsCounter command_crafter$allowNullPlayerListForStats(PlayerList instance, Player player, Operation<ServerStatsCounter> original, @Share("isDummy") LocalBooleanRef isDummy) {
        return instance != null || !isDummy.get() ? original.call(instance, player) : null;
    }

    @WrapOperation(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/players/PlayerList;getPlayerAdvancements(Lnet/minecraft/server/level/ServerPlayer;)Lnet/minecraft/server/PlayerAdvancements;"
            )
    )
    private PlayerAdvancements command_crafter$allowNullPlayerListForAdvancements(PlayerList instance, ServerPlayer serverPlayer, Operation<PlayerAdvancements> original, @Share("isDummy") LocalBooleanRef isDummy) {
        return instance != null || !isDummy.get() ? original.call(instance, serverPlayer) : null;
    }

    @WrapMethod(method = "level()Lnet/minecraft/server/level/ServerLevel;")
    private ServerLevel command_crafter$allowDummyWorld(Operation<ServerLevel> op) {
        //noinspection resource
        return super.level() instanceof DummyWorld ? null : op.call();
    }
}
