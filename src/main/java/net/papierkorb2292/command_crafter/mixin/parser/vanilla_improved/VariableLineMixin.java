package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.StringReader;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.commands.functions.CommandParser;
import net.minecraft.commands.functions.MacroFunction;
import net.papierkorb2292.command_crafter.parser.number_provider.TermNumberProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static net.papierkorb2292.command_crafter.helper.UtilKt.runWithValueSwap;

@Mixin(MacroFunction.MacroEntry.class)
public class VariableLineMixin<T extends ExecutionCommandSource<T>> {
    @WrapOperation(
            method = "instantiate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/functions/CommandFunction;parseCommand(Lnet/minecraft/commands/functions/CommandParser;Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/commands/execution/UnboundEntryAction;"
            )
    )
    private UnboundEntryAction<Runtime> command_crafter$disallowNumberProviderExpressions(CommandParser<Runtime> parser, StringReader input, Operation<UnboundEntryAction<Runtime>> op) {
        return runWithValueSwap(TermNumberProvider.INSTANCE.getDISALLOW_EXPRESSIONS(), true, () -> op.call(parser, input));
    }
}
