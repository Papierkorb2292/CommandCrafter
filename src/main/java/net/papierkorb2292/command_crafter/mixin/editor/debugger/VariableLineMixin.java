package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.command.SourcedCommandAction;
import net.minecraft.server.function.Macro;
import net.papierkorb2292.command_crafter.editor.debugger.helper.DebugPauseHandlerCreatorIndexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Macro.VariableLine.class)
public class VariableLineMixin<T> implements DebugPauseHandlerCreatorIndexConsumer {

    private Integer command_crafter$pauseHandlerCreatorIndex;

    @Override
    public void command_crafter$setPauseHandlerCreatorIndex(int index) {
        this.command_crafter$pauseHandlerCreatorIndex = index;
    }

    @ModifyReturnValue(
            method = "instantiate",
            at = @At("RETURN")
    )
    private SourcedCommandAction<T> command_crafter$addPauseHandlerCreatorIndexToAction(SourcedCommandAction<T> action) {
        if(this.command_crafter$pauseHandlerCreatorIndex != null
            && action instanceof DebugPauseHandlerCreatorIndexConsumer consumer) {
            consumer.command_crafter$setPauseHandlerCreatorIndex(this.command_crafter$pauseHandlerCreatorIndex);
        }
        return action;
    }
}
