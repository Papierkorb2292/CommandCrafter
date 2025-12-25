package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerCommonPacketListenerImpl.class)
public interface ServerCommonPacketListenerImplAccessor {

    @Invoker
    void callKeepConnectionAlive();

    @Accessor
    boolean getSuspendFlushingOnServerThread();
}
