package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.commands.ReloadCommand;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Collection;

@Mixin(ReloadCommand.class)
public interface ReloadCommandAccessor {
    @Invoker
    static Collection<String> callDiscoverNewPacks(PackRepository dataPackManager, WorldData saveProperties, Collection<String> enabledDataPacks) {
        throw new AssertionError();
    }
}
