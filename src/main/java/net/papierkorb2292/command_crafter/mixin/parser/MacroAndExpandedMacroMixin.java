package net.papierkorb2292.command_crafter.mixin.parser;

import net.minecraft.server.function.ExpandedMacro;
import net.minecraft.server.function.Macro;
import net.minecraft.util.Identifier;
import net.papierkorb2292.command_crafter.editor.debugger.DebugInformation;
import net.papierkorb2292.command_crafter.editor.debugger.helper.DebugInformationContainer;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionBreakpointLocation;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugFrame;
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType;
import net.papierkorb2292.command_crafter.parser.ParsedResourceCreator;
import net.papierkorb2292.command_crafter.parser.helper.FileSourceContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

@Mixin({Macro.class, ExpandedMacro.class})
public class MacroAndExpandedMacroMixin implements ParsedResourceCreator.ParseResourceContainer, DebugInformationContainer<FunctionBreakpointLocation, FunctionDebugFrame>, FileSourceContainer {

    private ParsedResourceCreator command_crafter$resourceCreator;
    private DebugInformation<FunctionBreakpointLocation, FunctionDebugFrame> command_crafter$debugInformation;
    private List<String> command_crafter$fileSourceLines;
    private Identifier command_crafter$fileSourceId;
    private PackContentFileType command_crafter$fileSourceType;

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
    public void command_crafter$setFileSource(@NotNull List<String> lines, @NotNull Identifier fileId, @NotNull PackContentFileType fileType) {
        this.command_crafter$fileSourceLines = lines;
        this.command_crafter$fileSourceId = fileId;
        this.command_crafter$fileSourceType = fileType;
    }

    @Override
    public List<String> command_crafter$getFileSourceLines() {
        return command_crafter$fileSourceLines;
    }

    @Nullable
    @Override
    public Identifier command_crafter$getFileSourceId() {
        return command_crafter$fileSourceId;
    }

    @Nullable
    @Override
    public PackContentFileType command_crafter$getFileSourceType() {
        return command_crafter$fileSourceType;
    }
}
