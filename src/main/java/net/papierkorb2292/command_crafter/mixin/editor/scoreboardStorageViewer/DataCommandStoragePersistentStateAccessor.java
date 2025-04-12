package net.papierkorb2292.command_crafter.mixin.editor.scoreboardStorageViewer;

import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.stream.Stream;

@Mixin(targets = "net.minecraft.command.DataCommandStorage$PersistentState")
public interface DataCommandStoragePersistentStateAccessor {
    @Invoker
    Stream<Identifier> callGetIds(String namespace);
}
