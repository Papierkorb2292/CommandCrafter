package net.papierkorb2292.command_crafter.mixin.editor.scoreboardStorageViewer;

import net.minecraft.command.DataCommandStorage;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.ServerScoreboardStorageFileSystem;
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.FileChangeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DataCommandStorage.class)
public abstract class DataCommandStorageMixin {
    @Shadow public abstract NbtCompound get(Identifier id);

    @Inject(
            method = "method_52613",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/DataCommandStorage;createStorage(Ljava/lang/String;)Lnet/minecraft/command/DataCommandStorage$PersistentState;"
            )
    )
    private void command_crafter$notifyFileSystemOfStorageCreation(String namespace, NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup, CallbackInfoReturnable<?> cir) {
        for(final var key : nbt.getKeys()) {
            ServerScoreboardStorageFileSystem.Companion.onFileUpdate(
                    ServerScoreboardStorageFileSystem.Directory.STORAGES,
                    namespace + ":" + key,
                    FileChangeType.Created
            );
        }
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
