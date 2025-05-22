package net.papierkorb2292.command_crafter.mixin.editor.scoreboardStorageViewer;

import net.minecraft.command.DataCommandStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(DataCommandStorage.class)
public interface DataCommandStorageAccessor {
    @Accessor
    Map<String, ?> getStorages();
}
