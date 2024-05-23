package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import net.minecraft.command.argument.CommandFunctionArgumentType;
import net.minecraft.server.MinecraftServer;
import net.papierkorb2292.command_crafter.editor.debugger.BreakpointParser;
import net.papierkorb2292.command_crafter.editor.debugger.helper.DebugInformationContainer;
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.ArgumentBreakpointParserSupplier;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionBreakpointLocation;
import net.papierkorb2292.command_crafter.parser.helper.MutableFunctionArgument;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CommandFunctionArgumentType.class)
public class CommandFunctionArgumentTypeMixin implements ArgumentBreakpointParserSupplier {
    @Nullable
    @Override
    public BreakpointParser<FunctionBreakpointLocation> command_crafter$getBreakpointParser(@Nullable Object argument, @NotNull MinecraftServer server) {
        if (!(argument instanceof MutableFunctionArgument functionArgument)) {
            return null;
        }
        if(functionArgument.isTag()) {
            //TODO
            return null;
        }
        var commandFunction = server.getCommandFunctionManager().getFunction(functionArgument.getId());
        //noinspection unchecked
        return commandFunction.map(function ->
                ((DebugInformationContainer<FunctionBreakpointLocation, ?>) function)
                        .command_crafter$getDebugInformation()
        ).orElse(null);
    }
}
