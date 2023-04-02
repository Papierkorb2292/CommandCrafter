package net.papierkorb2292.command_crafter.mixin.editor;

import net.minecraft.server.MinecraftServer;
import net.papierkorb2292.command_crafter.editor.EditorConnectionManager;
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer;
import net.papierkorb2292.command_crafter.editor.MinecraftServerConnection;
import net.papierkorb2292.command_crafter.editor.SocketEditorConnectionType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin implements MinecraftServerConnection {

    private @Nullable EditorConnectionManager command_crafter$editorConnectionManager;

    @Inject(
            method = "runServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Util;getMeasuringTimeMs()J",
                    ordinal = 0
            )
    )
    private void command_crafter$startLanguageServerConnector(CallbackInfo ci) {
        command_crafter$editorConnectionManager = new EditorConnectionManager(
                new SocketEditorConnectionType(52853), //TODO: Let the user change the port
                () -> new MinecraftLanguageServer(this)
        );
        command_crafter$editorConnectionManager.startServer();
    }

    @Inject(
            method = "shutdown",
            at = @At("HEAD")
    )
    private void command_crafter$shutdownLanguageServerConnector(CallbackInfo ci) {
        var connector = command_crafter$editorConnectionManager;
        if(connector != null)
            connector.stopServer();
    }
}
