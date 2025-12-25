package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.commands.functions.MacroFunction;
import net.papierkorb2292.command_crafter.editor.debugger.helper.DebugPauseHandlerCreatorIndexConsumer;
import net.papierkorb2292.command_crafter.editor.debugger.helper.IsMacroContainer;
import net.papierkorb2292.command_crafter.mixin.editor.debugger.BuildContextsAccessor;
import net.papierkorb2292.command_crafter.parser.helper.CursorOffsetContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MacroFunction.MacroEntry.class)
public class VariableLineMixin<T> implements DebugPauseHandlerCreatorIndexConsumer, CursorOffsetContainer {

    private Integer command_crafter$pauseHandlerCreatorIndex;
    private int command_crafter$readCharacters;
    private int command_crafter$skippedChars;

    @Override
    public void command_crafter$setPauseHandlerCreatorIndex(int index) {
        this.command_crafter$pauseHandlerCreatorIndex = index;
    }

    @ModifyReturnValue(
            method = "instantiate",
            at = @At("RETURN")
    )
    private UnboundEntryAction<T> command_crafter$addPauseHandlerCreatorIndexToAction(UnboundEntryAction<T> action) {
        if(this.command_crafter$pauseHandlerCreatorIndex != null
            && action instanceof DebugPauseHandlerCreatorIndexConsumer consumer) {
            consumer.command_crafter$setPauseHandlerCreatorIndex(this.command_crafter$pauseHandlerCreatorIndex);
        }
        return action;
    }

    @ModifyReturnValue(
            method = "instantiate",
            at = @At("RETURN")
    )
    private UnboundEntryAction<T> command_crafter$addCursorOffsetToAction(UnboundEntryAction<T> action) {
        if((command_crafter$readCharacters != 0 || command_crafter$skippedChars != 0) && action instanceof BuildContextsAccessor<?> accessor) {
            var contexts = accessor.getCommand().getTopContext();
            while(contexts != null) {
                for(var parsedNodes : contexts.getNodes()) {
                    ((CursorOffsetContainer)parsedNodes).command_crafter$setCursorOffset(command_crafter$readCharacters, command_crafter$skippedChars);
                }
                contexts = contexts.getChild();
            }
        }
        return action;
    }

    @ModifyReturnValue(
            method = "instantiate",
            at = @At("RETURN")
    )
    private UnboundEntryAction<T> command_crafter$setIsMacro(UnboundEntryAction<T> action) {
        if(action instanceof BuildContextsAccessor<?> accessor) {
            ((IsMacroContainer)accessor.getCommand()).command_crafter$setIsMacro(true);
        }
        return action;
    }

    @Override
    public void command_crafter$setCursorOffset(int readCharacters, int skippedChars) {
        this.command_crafter$readCharacters = readCharacters;
        this.command_crafter$skippedChars = skippedChars;
    }

    @Override
    public int command_crafter$getReadCharacters() {
        return this.command_crafter$readCharacters;
    }

    @Override
    public int command_crafter$getSkippedChars() {
        return this.command_crafter$skippedChars;
    }
}
