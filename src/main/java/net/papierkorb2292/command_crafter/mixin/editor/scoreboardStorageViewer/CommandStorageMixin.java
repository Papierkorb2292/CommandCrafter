package net.papierkorb2292.command_crafter.mixin.editor.scoreboardStorageViewer;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.level.storage.CommandStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.ServerScoreboardStorageFileSystem;
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.FileChangeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CommandStorage.class)
public abstract class CommandStorageMixin {
    @Shadow public abstract CompoundTag get(Identifier id);

    @ModifyExpressionValue(
            method = "getOrCreateContainer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/storage/DimensionDataStorage;computeIfAbsent(Lnet/minecraft/world/level/saveddata/SavedDataType;)Lnet/minecraft/world/level/saveddata/SavedData;"
            )
    )
    private SavedData command_crafter$notifyFileSystemOfStorageCreationOrLoad(SavedData original, String namespace) {
        if(original != null) {
            ((DataCommandStoragePersistentStateAccessor) original).callGetKeys(namespace).forEach(id -> {
                ServerScoreboardStorageFileSystem.Companion.onFileUpdate(
                        ServerScoreboardStorageFileSystem.Directory.STORAGES,
                        id.toString(),
                        FileChangeType.Created
                );
            });
        }
        return original;
    }

    @ModifyExpressionValue(
            method = "getContainer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/storage/DimensionDataStorage;get(Lnet/minecraft/world/level/saveddata/SavedDataType;)Lnet/minecraft/world/level/saveddata/SavedData;"
            )
    )
    private SavedData command_crafter$notifyFileSystemOfStorageLoad(SavedData original, String namespace) {
        if(original != null) {
            ((DataCommandStoragePersistentStateAccessor) original).callGetKeys(namespace).forEach(id -> {
                ServerScoreboardStorageFileSystem.Companion.onFileUpdate(
                        ServerScoreboardStorageFileSystem.Directory.STORAGES,
                        id.toString(),
                        FileChangeType.Created
                );
            });
        }
        return original;
    }

    @Inject(
            method = "set",
            at = @At("HEAD")
    )
    private void command_crafter$notifyFileSystemOfStorageChangeOrDeletion(Identifier id, CompoundTag nbt, CallbackInfo ci) {
        if(get(id).isEmpty()) {
            ServerScoreboardStorageFileSystem.Companion.onFileUpdate(
                    ServerScoreboardStorageFileSystem.Directory.STORAGES,
                    id.toString(),
                    FileChangeType.Created
            );
            return;

        }
        ServerScoreboardStorageFileSystem.Companion.onFileUpdate(
                ServerScoreboardStorageFileSystem.Directory.STORAGES,
                id.toString(),
                nbt.isEmpty() ? FileChangeType.Deleted : FileChangeType.Changed
        );
    }
}
