package net.papierkorb2292.command_crafter.mixin.editor.lsp4j;

import org.eclipse.lsp4j.jsonrpc.StandardLauncher;
import org.eclipse.lsp4j.jsonrpc.json.ConcurrentMessageProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StandardLauncher.class)
public interface StandardLauncherAccessor {

    @Accessor
    ConcurrentMessageProcessor getMsgProcessor();
}
