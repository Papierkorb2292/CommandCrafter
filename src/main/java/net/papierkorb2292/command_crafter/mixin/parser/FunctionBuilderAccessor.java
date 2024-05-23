package net.papierkorb2292.command_crafter.mixin.parser;

import net.minecraft.server.command.AbstractServerCommandSource;
import net.minecraft.server.function.FunctionBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FunctionBuilder.class)
public interface FunctionBuilderAccessor {

    @Invoker("<init>")
    static <T extends AbstractServerCommandSource<T>> FunctionBuilder<T> init() {
        throw new AssertionError();
    }
}
