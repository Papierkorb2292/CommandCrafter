package net.papierkorb2292.command_crafter.mixin.editor.scoreboardStorageViewer;

import net.minecraft.world.level.storage.CommandStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(CommandStorage.class)
public interface CommandStorageAccessor {
    @Accessor
    Map<String, ?> getNamespaces();
}
