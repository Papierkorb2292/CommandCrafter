package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.datafixers.util.Either;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.RawZipResourceCreator;
import net.papierkorb2292.command_crafter.parser.helper.RawResource;
import net.papierkorb2292.command_crafter.parser.helper.UnparsableArgumentType;
import net.papierkorb2292.command_crafter.parser.helper.UnparsableCommandNode;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Collections;
import java.util.List;

@SuppressWarnings("unused")
@Mixin(ArgumentCommandNode.class)
public class ArgumentCommandNodeMixin<S, T> implements UnparsableCommandNode {


    @Shadow(remap = false) @Final private ArgumentType<T> type;

    @Shadow(remap = false) @Final private String name;

    @Override
    public @NotNull List<Either<String, RawResource>> command_crafter$unparseNode(@NotNull CommandContext<ServerCommandSource> context, @NotNull StringRange range, @NotNull DirectiveStringReader<RawZipResourceCreator> reader) throws CommandSyntaxException {
        if(type instanceof UnparsableArgumentType unparsable) {
            var result = unparsable.command_crafter$unparseArgument(context, name, reader);
            if(result != null) {
                return result;
            }
        }
        return Collections.singletonList(Either.left(UnparsableCommandNode.Companion.unparseNodeFromStringRange(context, range)));
    }
}
