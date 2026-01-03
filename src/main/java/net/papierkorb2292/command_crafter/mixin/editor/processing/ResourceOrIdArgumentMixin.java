package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.Codec;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceOrIdArgument;
import net.minecraft.core.Registry;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.parsing.packrat.commands.Grammar;
import net.papierkorb2292.command_crafter.editor.processing.*;
import net.papierkorb2292.command_crafter.editor.processing.helper.*;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(ResourceOrIdArgument.class)
public class ResourceOrIdArgumentMixin<T> implements AnalyzingCommandNode {

    @Shadow @Final private Codec<T> codec;
    @Shadow @Final private HolderLookup.Provider registryLookup;
    @Shadow @Final private Grammar<ResourceOrIdArgument.Result<T, Tag>> grammar;
    @Shadow @Final private ResourceKey<? extends Registry<T>> registryKey;
    private PackContentFileType command_crafter$packContentFileType = null;

    private Codec<?> command_crafter$inlineOrReferenceCodec = null;

    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    private void command_crafter$selectPackContentFileType(CommandBuildContext commandBuildContext, ResourceKey<? extends Registry<?>> resourceKey, Codec<?> codec, CallbackInfo ci) {
        command_crafter$packContentFileType = PackContentFileType.Companion.getOrCreateTypeForDynamicRegistry(resourceKey);
    }

    @Override
    public void command_crafter$analyze(@NotNull CommandContext<SharedSuggestionProvider> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> reader, @NotNull AnalyzingResult result, @NotNull String name) throws CommandSyntaxException {
        var treeBuilder = new StringRangeTree.Builder<Tag>();
        var partialBuilder = new StringRangeTree.PartialBuilder<Tag>();

        ResourceOrIdArgument.Result<T, Tag> parsed;

        try {
            PackratParserAdditionalArgs.INSTANCE.getNbtStringRangeTreeBuilder().set(new PackratParserAdditionalArgs.StringRangeTreeBranchingArgument<>(partialBuilder));
            PackratParserAdditionalArgs.INSTANCE.getAllowMalformed().set(true);
            parsed = grammar.parseForCommands(reader);
        } catch(CommandSyntaxException e) {
            parsed = new ResourceOrIdArgument.InlineResult<>(EndTag.INSTANCE);
            var node = partialBuilder.pushNode();
            node.setNode(EndTag.INSTANCE);
            node.setNodeAllowedStart(range.getStart());
            node.setStartCursor(range.getStart());
            node.setEndCursor(range.getEnd());
        } finally {
            PackratParserAdditionalArgs.INSTANCE.getNbtStringRangeTreeBuilder().remove();
            PackratParserAdditionalArgs.INSTANCE.getAllowMalformed().remove();
        }

        Tag treeRoot;

        switch(parsed) {
            case ResourceOrIdArgument.ReferenceResult<T, Tag>(ResourceKey<T> key) -> {
                // Analyze up until the next space instead of just analyzing the given range,
                // because otherwise it can analyze the entire rest of the line when invoked through tryAnalyzeNextNode,
                // which is especially problematic for macros, where there might be more nodes later in the line
                var argumentEndCursor = range.getStart();
                while(argumentEndCursor < reader.getString().length() && reader.getString().charAt(argumentEndCursor) != ' ')
                    argumentEndCursor++;

                var argumentRange = new StringRange(range.getStart(), argumentEndCursor);

                if(command_crafter$packContentFileType != null)
                    IdArgumentTypeAnalyzer.INSTANCE.analyzeForId(key.identifier(), command_crafter$packContentFileType, argumentRange, result, reader);
                else
                    result.getSemanticTokens().addMultiline(argumentRange, TokenType.Companion.getPARAMETER(), 0);

                treeRoot = StringTag.valueOf(key.identifier().toString());
                treeBuilder.addNode(treeRoot, argumentRange, argumentRange.getStart());
            }
            case ResourceOrIdArgument.InlineResult<T, Tag>(Tag value) -> {
                treeRoot = value;
                partialBuilder.addToBasicBuilder(treeBuilder);
            }
        }

        var isInline = parsed instanceof ResourceOrIdArgument.InlineResult<T, Tag>;

        if(command_crafter$inlineOrReferenceCodec == null)
            command_crafter$inlineOrReferenceCodec = RegistryFileCodec.create(registryKey, codec);

        var tree = treeBuilder.build(treeRoot);
        var treeOperations = StringRangeTree.TreeOperations.Companion.forNbt(
                tree,
                reader
        )
                .withSuggestionResolver(new NbtSuggestionResolver(reader, nbtString -> Identifier.tryParse(nbtString.value()) == null))
                .withRegistry(registryLookup);
        if(!isInline)
            treeOperations = treeOperations.withDiagnosticSeverity(null);
        treeOperations.analyzeFull(result, isInline, command_crafter$inlineOrReferenceCodec);
    }

    @WrapMethod(method = "listSuggestions")
    private CompletableFuture<Suggestions> command_crafter$skipVanillaSuggestions(CommandContext<?> context, SuggestionsBuilder suggestionsBuilder, Operation<CompletableFuture<Suggestions>> op) {
        if(getOrNull(VanillaLanguage.Companion.getSUGGESTIONS_FULL_INPUT()) != null)
            return suggestionsBuilder.buildFuture();
        return op.call(context, suggestionsBuilder);
    }
}