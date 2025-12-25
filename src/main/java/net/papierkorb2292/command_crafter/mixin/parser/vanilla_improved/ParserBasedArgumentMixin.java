package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import net.minecraft.util.parsing.packrat.commands.ParserBasedArgument;
import net.minecraft.commands.CommandSourceStack;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.RawZipResourceCreator;
import net.papierkorb2292.command_crafter.parser.helper.RawResource;
import net.papierkorb2292.command_crafter.parser.helper.StringifiableArgumentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.ArrayList;
import java.util.List;

@Mixin(ParserBasedArgument.class)
public abstract class ParserBasedArgumentMixin<T> implements StringifiableArgumentType {
    @Shadow public abstract T parse(StringReader reader) throws CommandSyntaxException;

    @Nullable
    @Override
    public List<Either<String, RawResource>> command_crafter$stringifyArgument(@NotNull CommandContext<CommandSourceStack> context, @NotNull String name, @NotNull DirectiveStringReader<RawZipResourceCreator> reader) throws CommandSyntaxException {
        PackratParserAdditionalArgs.INSTANCE.getStringifiedArgument().set(new PackratParserAdditionalArgs.StringifiedBranchingArgument(new ArrayList<>()));
        parse(reader);
        var result = PackratParserAdditionalArgs.INSTANCE.getStringifiedArgument().get();
        PackratParserAdditionalArgs.INSTANCE.getStringifiedArgument().remove();
        return result.getStringified();
    }
}
