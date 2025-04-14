package net.papierkorb2292.command_crafter.mixin.editor.scoreboardStorageViewer;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.command.DataCommandStorage;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.ServerScoreboardStorageFileSystem;
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.FileChangeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DataCommandStorage.class)
public abstract class DataCommandStorageMixin {
    @Shadow public abstract NbtCompound get(Identifier id);

    @ModifyExpressionValue(
            method = "getOrCreateStorage",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/PersistentStateManager;getOrCreate(Lnet/minecraft/world/PersistentStateType;)Lnet/minecraft/world/PersistentState;"
            )
    )
    private PersistentState command_crafter$notifyFileSystemOfStorageCreationOrLoad(PersistentState original, String namespace) {
        if(original != null) {
            ((DataCommandStoragePersistentStateAccessor) original).callGetIds(namespace).forEach(id -> {
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
            method = "getStorage",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/PersistentStateManager;get(Lnet/minecraft/world/PersistentStateType;)Lnet/minecraft/world/PersistentState;"
            )
    )
    private PersistentState command_crafter$notifyFileSystemOfStorageLoad(PersistentState original, String namespace) {
        if(original != null) {
            ((DataCommandStoragePersistentStateAccessor) original).callGetIds(namespace).forEach(id -> {
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
    private void command_crafter$notifyFileSystemOfStorageChangeOrDeletion(Identifier id, NbtCompound nbt, CallbackInfo ci) {
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
