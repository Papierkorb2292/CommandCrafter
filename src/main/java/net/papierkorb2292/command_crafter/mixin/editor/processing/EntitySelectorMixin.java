package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.tree.RootCommandNode;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.util.CompilableString;
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.MalformedStringDecoderAnalyzing;
import net.papierkorb2292.command_crafter.parser.Language;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Function;

@Mixin(EntitySelector.class)
public class EntitySelectorMixin {

    @WrapOperation(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/CompilableString;codec(Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"
            )
    )
    private static <T> Codec<CompilableString<T>> command_crafter$analyzeEmbeddedSelector(Function<String, DataResult<T>> compiler, Operation<Codec<CompilableString<T>>> op) {
        final var parser = (CompilableString.CommandParserHelper<T>)compiler;
        final var rootNode = new RootCommandNode<SharedSuggestionProvider>();
        rootNode.addChild(RequiredArgumentBuilder.<SharedSuggestionProvider, EntitySelector>argument("entity", EntityArgument.entities()).build());
        final var entityNode = rootNode.getChild("entity");
        final var language = new VanillaLanguage();
        final var stringAnalyzing = new MalformedStringDecoderAnalyzing<>(
                _ -> null,
                (_, result, behavior, reader) -> {
                    reader.enterClosure(new Language.TopLevelClosure(language));
                    language.analyzeCommandNode(
                            new ParsedCommandNode<>(entityNode, new StringRange(reader.getCursor(), reader.getRemainingLength())),
                            new ParsedCommandNode<>(rootNode, StringRange.at(reader.getCursor())),
                            new CommandContextBuilder<>(reader.getDispatcher(), reader.getResourceCreator().getSource(), rootNode, reader.getCursor()),
                            result,
                            reader,
                            false
                    );
                }
        );
        return stringAnalyzing.wrapCodec(op.call(stringAnalyzing.wrapCommandParserHelper(parser)));
    }
}
