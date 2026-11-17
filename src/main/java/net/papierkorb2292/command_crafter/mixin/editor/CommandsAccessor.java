package net.papierkorb2292.command_crafter.mixin.editor;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.execution.ExecutionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Commands.class)
public interface CommandsAccessor {
    @Accessor
    static ThreadLocal<ExecutionContext<CommandSourceStack>> getCURRENT_EXECUTION_CONTEXT() {
        throw new AssertionError();
    }
}
