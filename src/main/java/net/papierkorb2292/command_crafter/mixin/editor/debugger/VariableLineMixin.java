package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.command.SourcedCommandAction;
import net.minecraft.server.function.Macro;
import net.papierkorb2292.command_crafter.editor.debugger.helper.DebugPauseHandlerCreatorIndexConsumer;
import net.papierkorb2292.command_crafter.editor.debugger.helper.IsMacroContainer;
import net.papierkorb2292.command_crafter.parser.helper.CursorOffsetContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Macro.VariableLine.class)
public class VariableLineMixin<T> implements DebugPauseHandlerCreatorIndexConsumer, CursorOffsetContainer {

    private Integer command_crafter$pauseHandlerCreatorIndex;
    private int command_crafter$readCharacters;
    private int command_crafter$skippedChars;

    @Override
    public void command_crafter$setPauseHandlerCreatorIndex(int index) {
        this.command_crafter$pauseHandlerCreatorIndex = index;
    }

    @ModifyReturnValue(
            method = "instantiate(Ljava/util/List;Lcom/mojang/brigadier/CommandDispatcher;Lnet/minecraft/server/command/AbstractServerCommandSource;Lnet/minecraft/util/Identifier;)Lnet/minecraft/command/SourcedCommandAction;",
            at = @At("RETURN")
    )
    private SourcedCommandAction<T> command_crafter$addPauseHandlerCreatorIndexToAction(SourcedCommandAction<T> action) {
        if(this.command_crafter$pauseHandlerCreatorIndex != null
            && action instanceof DebugPauseHandlerCreatorIndexConsumer consumer) {
            consumer.command_crafter$setPauseHandlerCreatorIndex(this.command_crafter$pauseHandlerCreatorIndex);
        }
        return action;
    }

    @ModifyReturnValue(
            method = "instantiate(Ljava/util/List;Lcom/mojang/brigadier/CommandDispatcher;Lnet/minecraft/server/command/AbstractServerCommandSource;Lnet/minecraft/util/Identifier;)Lnet/minecraft/command/SourcedCommandAction;",
            at = @At("RETURN")
    )
    private SourcedCommandAction<T> command_crafter$addCursorOffsetToAction(SourcedCommandAction<T> action) {
        if((command_crafter$readCharacters != 0 || command_crafter$skippedChars != 0) && action instanceof SingleCommandActionAccessor<?> accessor) {
            var contexts = accessor.getContextChain().getTopContext();
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
            method = "instantiate(Ljava/util/List;Lcom/mojang/brigadier/CommandDispatcher;Lnet/minecraft/server/command/AbstractServerCommandSource;Lnet/minecraft/util/Identifier;)Lnet/minecraft/command/SourcedCommandAction;",
            at = @At("RETURN")
    )
    private SourcedCommandAction<T> command_crafter$setIsMacro(SourcedCommandAction<T> action) {
        if(action instanceof SingleCommandActionAccessor<?> accessor) {
            ((IsMacroContainer)accessor.getContextChain()).command_crafter$setIsMacro(true);
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
