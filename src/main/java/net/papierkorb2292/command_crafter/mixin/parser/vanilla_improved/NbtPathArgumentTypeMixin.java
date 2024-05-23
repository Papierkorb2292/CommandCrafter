package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.datafixers.util.Either;
import net.minecraft.command.argument.NbtPathArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.RawZipResourceCreator;
import net.papierkorb2292.command_crafter.parser.helper.NbtPathFilteredRootNodeFilterProvider;
import net.papierkorb2292.command_crafter.parser.helper.RawResource;
import net.papierkorb2292.command_crafter.parser.helper.StringifiableArgumentType;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collections;
import java.util.List;

@Mixin(NbtPathArgumentType.class)
public class NbtPathArgumentTypeMixin implements StringifiableArgumentType {

    @ModifyExpressionValue(
            method = "readName",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;peek()C",
                    ordinal = 0,
                    remap = false
            )
    )
    private static char command_crafter$endTagOnNewline(char c, StringReader reader) {
        return VanillaLanguage.Companion.isReaderEasyNextLine(reader) && c == '\n' ? ' ' : c;
    }

    @ModifyExpressionValue(
            method = "parse(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/command/argument/NbtPathArgumentType$NbtPath;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;peek()C",
                    remap = false
            )
    )
    private char command_crafter$endPathOnNewline(char c, StringReader reader) {
        return VanillaLanguage.Companion.isReaderEasyNextLine(reader) && c == '\n' ? ' ' : c;
    }

    @Override
    public List<Either<String, RawResource>> command_crafter$stringifyArgument(@NotNull CommandContext<ServerCommandSource> context, @NotNull String name, @NotNull DirectiveStringReader<RawZipResourceCreator> reader) {
        var argument = context.getArgument(name, NbtPathArgumentType.NbtPath.class);
        var sourceString = argument.getString();
        var nodes = ((NbtPathArgumentTypeNbtPathAccessor) argument).getNodes();
        var nodeEndIndices = ((NbtPathArgumentTypeNbtPathAccessor) argument).getNodeEndIndices();
        var lastEndIndex = 0;
        var result = new StringBuilder();

        for(var node : nodes) {
            var endIndex = nodeEndIndices.getInt(node);
            if(node instanceof NbtPathArgumentTypeFilteredListElementNodeAccessor filteredListNode) {
                result.append("[{");
                result.append(filteredListNode.getFilter().asString());
                result.append("}]");
            } else if(node instanceof NbtPathArgumentTypeFilteredNamedNodeAccessor filteredNamedNode) {
                result.append(".");
                result.append(StringArgumentType.escapeIfRequired(filteredNamedNode.getName()));
                result.append("{");
                result.append(filteredNamedNode.getFilter().asString());
                result.append("}");
            } else if(node instanceof NbtPathFilteredRootNodeFilterProvider rootNodeFilter) {
                result.append("{");
                result.append(rootNodeFilter.command_crafter$getFilter().asString());
                result.append("}");
            } else {
                result.append(sourceString, lastEndIndex, endIndex);
            }
            lastEndIndex = endIndex;
        }

        return Collections.singletonList(Either.left(result.toString()));
    }

}
