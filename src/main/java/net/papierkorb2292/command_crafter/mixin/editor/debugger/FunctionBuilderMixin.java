package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.command.SourcedCommandAction;
import net.minecraft.server.command.AbstractServerCommandSource;
import net.minecraft.server.function.CommandFunction;
import net.minecraft.server.function.FunctionBuilder;
import net.papierkorb2292.command_crafter.editor.debugger.DebugInformation;
import net.papierkorb2292.command_crafter.editor.debugger.helper.DebugInformationContainer;
import net.papierkorb2292.command_crafter.editor.debugger.helper.DebugPauseHandlerCreatorIndexConsumer;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionBreakpointLocation;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(FunctionBuilder.class)
public class FunctionBuilderMixin<T extends AbstractServerCommandSource<T>> implements DebugPauseHandlerCreatorIndexConsumer, DebugInformationContainer<FunctionBreakpointLocation, FunctionDebugFrame> {

    private @Nullable Integer command_crafter$pauseHandlerCreatorIndex;
    private @Nullable DebugInformation<FunctionBreakpointLocation, FunctionDebugFrame> command_crafter$debugInformation;

    @Override
    public void command_crafter$setPauseHandlerCreatorIndex(int index) {
        this.command_crafter$pauseHandlerCreatorIndex = index;
    }

    @ModifyVariable(
            method = "addAction",
            at = @At("HEAD"),
            argsOnly = true
    )
    private SourcedCommandAction<T> command_crafter$addPauseHandlerCreatorIndexToAction(SourcedCommandAction<T> action) {
        if(this.command_crafter$pauseHandlerCreatorIndex != null
            && action instanceof DebugPauseHandlerCreatorIndexConsumer consumer) {
            consumer.command_crafter$setPauseHandlerCreatorIndex(this.command_crafter$pauseHandlerCreatorIndex);
        }
        return action;
    }

    @ModifyArg(
            method = "addMacroCommand",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;add(Ljava/lang/Object;)Z",
                    ordinal = 1
            )
    )
    private Object command_crafter$addPauseHandlerCreatorIndexToMacro(Object variableLine) {
        if(this.command_crafter$pauseHandlerCreatorIndex != null) {
            ((DebugPauseHandlerCreatorIndexConsumer)variableLine).command_crafter$setPauseHandlerCreatorIndex(this.command_crafter$pauseHandlerCreatorIndex);
        }
        return variableLine;
    }

    @ModifyReturnValue(
            method = "toCommandFunction",
            at = @At("RETURN")
    )
    private CommandFunction<T> command_crafter$addDebugInformation(CommandFunction<T> function) {
        if(this.command_crafter$debugInformation != null && function instanceof DebugInformationContainer<?,?>) {
            //noinspection unchecked
            ((DebugInformationContainer<FunctionBreakpointLocation, FunctionDebugFrame>) function)
                    .command_crafter$setDebugInformation(this.command_crafter$debugInformation);
        }
        return function;
    }

    @Nullable
    @Override
    public DebugInformation<FunctionBreakpointLocation, FunctionDebugFrame> command_crafter$getDebugInformation() {
        return this.command_crafter$debugInformation;
    }

    @Override
    public void command_crafter$setDebugInformation(@NotNull DebugInformation<FunctionBreakpointLocation, FunctionDebugFrame> debugInformation) {
        this.command_crafter$debugInformation = debugInformation;
    }
}
