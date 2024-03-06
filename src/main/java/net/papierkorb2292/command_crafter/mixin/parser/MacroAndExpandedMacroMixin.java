package net.papierkorb2292.command_crafter.mixin.parser;

import net.minecraft.server.function.ExpandedMacro;
import net.minecraft.server.function.Macro;
import net.papierkorb2292.command_crafter.editor.debugger.DebugInformation;
import net.papierkorb2292.command_crafter.editor.debugger.helper.DebugInformationContainer;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionBreakpointLocation;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugFrame;
import net.papierkorb2292.command_crafter.parser.ParsedResourceCreator;
import net.papierkorb2292.command_crafter.parser.helper.FileLinesContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

@Mixin({Macro.class, ExpandedMacro.class})
public class MacroAndExpandedMacroMixin implements ParsedResourceCreator.ParseResourceContainer, DebugInformationContainer<FunctionBreakpointLocation, FunctionDebugFrame>, FileLinesContainer {

    private ParsedResourceCreator command_crafter$resourceCreator;
    private DebugInformation<FunctionBreakpointLocation, FunctionDebugFrame> command_crafter$debugInformation;
    private List<String> command_crafter$lines;

    public void command_crafter$setResourceCreator(ParsedResourceCreator command_crafter$resourceCreator) {
        this.command_crafter$resourceCreator = command_crafter$resourceCreator;
    }

    public ParsedResourceCreator command_crafter$getResourceCreator() {
        return command_crafter$resourceCreator;
    }

    @Nullable
    @Override
    public DebugInformation<FunctionBreakpointLocation, FunctionDebugFrame> command_crafter$getDebugInformation() {
        return command_crafter$debugInformation;
    }

    @Override
    public void command_crafter$setDebugInformation(@NotNull DebugInformation<FunctionBreakpointLocation, FunctionDebugFrame> debugInformation) {
        this.command_crafter$debugInformation = debugInformation;
    }

    @Override
    public void command_crafter$setLines(@NotNull List<String> lines) {
        this.command_crafter$lines = lines;
    }

    @Override
    public List<String> command_crafter$getLines() {
        return command_crafter$lines;
    }
}
