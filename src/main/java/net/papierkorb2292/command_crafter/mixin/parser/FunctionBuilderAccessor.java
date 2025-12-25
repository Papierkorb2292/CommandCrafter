package net.papierkorb2292.command_crafter.mixin.parser;

import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.functions.FunctionBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FunctionBuilder.class)
public interface FunctionBuilderAccessor {

    @Invoker("<init>")
    static <T extends ExecutionCommandSource<T>> FunctionBuilder<T> init() {
        throw new AssertionError();
    }
}
