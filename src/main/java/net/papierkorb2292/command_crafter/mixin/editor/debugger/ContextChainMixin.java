package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.mojang.brigadier.context.ContextChain;
import net.papierkorb2292.command_crafter.editor.debugger.helper.DebugPauseHandlerCreatorIndexConsumer;
import net.papierkorb2292.command_crafter.editor.debugger.helper.DebugPauseHandlerCreatorIndexProvider;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ContextChain.class)
public class ContextChainMixin implements DebugPauseHandlerCreatorIndexConsumer, DebugPauseHandlerCreatorIndexProvider {

    private Integer command_crafter$pauseHandlerCreatorIndex;

    @Override
    public void command_crafter$setPauseHandlerCreatorIndex(int index) {
        this.command_crafter$pauseHandlerCreatorIndex = index;
    }
    @Override
    public Integer command_crafter$getPauseHandlerCreatorIndex() {
        return this.command_crafter$pauseHandlerCreatorIndex;
    }
}
