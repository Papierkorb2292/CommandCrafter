package net.papierkorb2292.command_crafter.mixin.editor;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBox;
import net.minecraft.commands.ParseContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CommandBox.class)
public interface CommandBoxAccessor<C extends ParseContext> {
    @Accessor
    CommandDispatcher<C> getDispatcher();

    @Invoker
    static <T> T callRunWithParseContext(final ScopedValue.CallableOp<T, RuntimeException> action, final ParseContext context) {
        throw new AssertionError();
    }
}
