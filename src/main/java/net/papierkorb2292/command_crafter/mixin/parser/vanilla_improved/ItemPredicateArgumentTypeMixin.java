package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import net.minecraft.command.argument.ItemPredicateArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import net.papierkorb2292.command_crafter.mixin.CommandContextAccessor;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.RawZipResourceCreator;
import net.papierkorb2292.command_crafter.parser.helper.RawResource;
import net.papierkorb2292.command_crafter.parser.helper.UnparsableArgumentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.ArrayList;
import java.util.List;

@Mixin(ItemPredicateArgumentType.class)
public abstract class ItemPredicateArgumentTypeMixin implements UnparsableArgumentType {
    @Shadow public abstract ItemPredicateArgumentType.ItemStackPredicateArgument parse(StringReader stringReader) throws CommandSyntaxException;

    @Nullable
    @Override
    public List<Either<String, RawResource>> command_crafter$unparseArgument(@NotNull CommandContext<ServerCommandSource> context, @NotNull String name, @NotNull DirectiveStringReader<RawZipResourceCreator> reader) throws CommandSyntaxException {
        var readerCopy = reader.copy();
        readerCopy.setCursor(((CommandContextAccessor)context).getArguments().get(name).getRange().getStart());
        PackratParserAdditionalArgs.INSTANCE.getUnparsedArgument().set(new ArrayList<>());
        parse(readerCopy);
        var result = PackratParserAdditionalArgs.INSTANCE.getUnparsedArgument().get();
        PackratParserAdditionalArgs.INSTANCE.getUnparsedArgument().remove();
        return result;
    }
}
