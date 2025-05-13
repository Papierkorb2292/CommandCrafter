package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import net.minecraft.resource.ResourcePackManager;
import net.minecraft.server.command.ReloadCommand;
import net.minecraft.world.SaveProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Collection;

@Mixin(ReloadCommand.class)
public interface ReloadCommandAccessor {
    @Invoker
    static Collection<String> callFindNewDataPacks(ResourcePackManager dataPackManager, SaveProperties saveProperties, Collection<String> enabledDataPacks) {
        throw new AssertionError();
    }
}
