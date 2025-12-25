package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.datafixers.util.Either;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.commands.CommandSourceStack;
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

@Mixin(NbtPathArgument.class)
public class NbtPathArgumentMixin implements StringifiableArgumentType {

    @ModifyExpressionValue(
            method = "readUnquotedName",
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
            method = "parse(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/commands/arguments/NbtPathArgument$NbtPath;",
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
    public List<Either<String, RawResource>> command_crafter$stringifyArgument(@NotNull CommandContext<CommandSourceStack> context, @NotNull String name, @NotNull DirectiveStringReader<RawZipResourceCreator> reader) {
        var argument = context.getArgument(name, NbtPathArgument.NbtPath.class);
        var sourceString = argument.asString();
        var nodes = ((NbtPathArgumentTypeNbtPathAccessor) argument).getNodes();
        var nodeEndIndices = ((NbtPathArgumentTypeNbtPathAccessor) argument).getNodeToOriginalPosition();
        var lastEndIndex = 0;
        var result = new StringBuilder();

        for(var node : nodes) {
            var endIndex = nodeEndIndices.getInt(node);
            switch(node) {
                case NbtPathArgumentTypeFilteredListElementNodeAccessor filteredListNode -> {
                    result.append("[");
                    result.append(filteredListNode.getPattern().asString());
                    result.append("]");
                }
                case NbtPathArgumentTypeFilteredNamedNodeAccessor filteredNamedNode -> {
                    result.append(".");
                    result.append(StringArgumentType.escapeIfRequired(filteredNamedNode.getName()));
                    result.append(filteredNamedNode.getPattern().asString());
                }
                case NbtPathFilteredRootNodeFilterProvider rootNodeFilter -> {
                    result.append(rootNodeFilter.command_crafter$getFilter().asString());
                }
                default -> result.append(sourceString, lastEndIndex, endIndex);
            }
            lastEndIndex = endIndex;
        }

        return Collections.singletonList(Either.left(result.toString()));
    }

}
