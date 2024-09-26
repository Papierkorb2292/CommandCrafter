package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.ExecutionControl;
import net.minecraft.command.ExecutionFlags;
import net.minecraft.command.argument.CommandFunctionArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.function.CommandFunction;
import net.minecraft.server.function.ExpandedMacro;
import net.minecraft.server.function.Macro;
import net.papierkorb2292.command_crafter.editor.debugger.helper.CommandExecutionContextContinueCallback;
import net.papierkorb2292.command_crafter.editor.debugger.helper.PotentialDebugFrameInitiator;
import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.ExitDebugFrameCommandAction;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugFrame;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags.FunctionTagDebugFrame;
import net.papierkorb2292.command_crafter.mixin.editor.CommandManagerAccessor;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(targets = "net.minecraft.server.command.ExecuteCommand$IfUnlessRedirector")
public class ExecuteIfUnlessRedirectorMixin implements PotentialDebugFrameInitiator {
    @Override
    public boolean command_crafter$willInitiateDebugFrame(@NotNull CommandContext<ServerCommandSource> context) {
        try {
            CommandFunctionArgumentType.getFunctions(context, "name");
            return true;
        } catch (CommandSyntaxException e) {
            return false;
        }
    }

    @Override
    public boolean command_crafter$isInitializedDebugFrameEmpty(@NotNull CommandContext<ServerCommandSource> context) {
        try {
            return CommandFunctionArgumentType.getFunctions(context, "name").stream().allMatch(function ->
                    function instanceof ExpandedMacro<ServerCommandSource> expandedMacro && expandedMacro.entries().isEmpty()
                            || function instanceof Macro<ServerCommandSource> macro && ((MacroAccessor)macro).getLines().isEmpty()
            );
        } catch (CommandSyntaxException e) {
            return true;
        }
    }
}
