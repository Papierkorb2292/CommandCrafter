package net.papierkorb2292.command_crafter.mixin.editor.scoreboardStorageViewer;

import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.stream.Stream;

@Mixin(targets = "net.minecraft.world.level.storage.CommandStorage$Container")
public interface DataCommandStoragePersistentStateAccessor {
    @Invoker
    Stream<Identifier> callGetKeys(String namespace);
}
