package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.execution.ExecutionControl;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.commands.functions.PlainTextFunction;
import net.minecraft.commands.functions.MacroFunction;
import net.papierkorb2292.command_crafter.editor.debugger.helper.CommandExecutionContextContinueCallback;
import net.papierkorb2292.command_crafter.editor.debugger.helper.PotentialDebugFrameInitiator;
import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.ExitDebugFrameCommandAction;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugFrame;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags.FunctionTagDebugFrame;
import net.papierkorb2292.command_crafter.mixin.editor.CommandsAccessor;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(targets = "net.minecraft.server.commands.ExecuteCommand$ExecuteIfFunctionCustomModifier")
public class ExecuteIfUnlessRedirectorMixin implements PotentialDebugFrameInitiator {
    @Override
    public boolean command_crafter$willInitiateDebugFrame(@NotNull CommandContext<CommandSourceStack> context) {
        try {
            FunctionArgument.getFunctions(context, "name");
            return true;
        } catch (CommandSyntaxException e) {
            return false;
        }
    }

    @Override
    public boolean command_crafter$isInitializedDebugFrameEmpty(@NotNull CommandContext<CommandSourceStack> context) {
        try {
            return FunctionArgument.getFunctions(context, "name").stream().allMatch(function ->
                    function instanceof PlainTextFunction<CommandSourceStack> expandedMacro && expandedMacro.entries().isEmpty()
                            || function instanceof MacroFunction<CommandSourceStack> macro && ((MacroFunctionAccessor)macro).getEntries().isEmpty()
            );
        } catch (CommandSyntaxException e) {
            return true;
        }
    }
}
