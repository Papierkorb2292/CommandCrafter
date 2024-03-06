package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import net.minecraft.command.argument.BlockPredicateArgumentType;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.RawZipResourceCreator;
import net.papierkorb2292.command_crafter.parser.helper.RawResource;
import net.papierkorb2292.command_crafter.parser.helper.UnparsableArgumentType;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.ArrayList;
import java.util.List;

@Mixin(BlockPredicateArgumentType.class)
public class BlockPredicateArgumentTypeMixin implements UnparsableArgumentType {

    @SuppressWarnings("RedundantThrows")
    @Nullable
    @Override
    public List<Either<String, RawResource>> command_crafter$unparseArgument(@NotNull CommandContext<ServerCommandSource> context, @NotNull String name, @NotNull DirectiveStringReader<RawZipResourceCreator> reader) throws CommandSyntaxException {
        if(reader.peek() != '(' || !VanillaLanguage.Companion.isReaderInlineResources(reader)) {
            return null;
        }
        var entryList = VanillaLanguage.Companion.parseRawRegistryTagTuple(reader, Registries.BLOCK);
        List<Either<String, RawResource>> result = new ArrayList<>();
        result.add(Either.left("#"));
        result.add(Either.right(entryList.getResource()));
        while(reader.canRead()) {
            result.add(Either.left(reader.getRemaining()));
        }
        return result;
    }
}
