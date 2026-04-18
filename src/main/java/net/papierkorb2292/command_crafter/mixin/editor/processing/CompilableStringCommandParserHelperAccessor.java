package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.util.CompilableString;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CompilableString.CommandParserHelper.class)
public interface CompilableStringCommandParserHelperAccessor<T> {
    @Invoker
    T callParse(StringReader reader) throws CommandSyntaxException;
    @Invoker
    String callErrorMessage(String original, CommandSyntaxException exception);
}
