package net.papierkorb2292.command_crafter.mixin.parser;

import net.minecraft.server.function.CommandFunction;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionPauseContext;
import net.papierkorb2292.command_crafter.editor.debugger.DebugInformation;
import net.papierkorb2292.command_crafter.editor.debugger.helper.DebugInformationContainer;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionBreakpointLocation;
import net.papierkorb2292.command_crafter.parser.ParsedResourceCreator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CommandFunction.class)
public class CommandFunctionMixin implements ParsedResourceCreator.ParseResourceContainer, DebugInformationContainer<FunctionBreakpointLocation, FunctionPauseContext> {

    private ParsedResourceCreator command_crafter$resourceCreator;
    private DebugInformation<FunctionBreakpointLocation, FunctionPauseContext> command_crafter$debugInformation;

    public void command_crafter$setResourceCreator(ParsedResourceCreator command_crafter$resourceCreator) {
        this.command_crafter$resourceCreator = command_crafter$resourceCreator;
    }

    public ParsedResourceCreator command_crafter$getResourceCreator() {
        return command_crafter$resourceCreator;
    }

    @Nullable
    @Override
    public DebugInformation<FunctionBreakpointLocation, FunctionPauseContext> command_crafter$getDebugInformation() {
        return command_crafter$debugInformation;
    }

    @Override
    public void command_crafter$setDebugInformation(@NotNull DebugInformation<FunctionBreakpointLocation, FunctionPauseContext> debugInformation) {
        this.command_crafter$debugInformation = debugInformation;
    }
}
