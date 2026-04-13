package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.parsing.packrat.commands.Grammar;
import net.papierkorb2292.command_crafter.editor.processing.BranchBehaviorProvider;
import net.papierkorb2292.command_crafter.editor.processing.helper.*;
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.DataObjectDecoding;
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.MalformedStringDecoderAnalyzing;
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.StringRangeTree;
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.TreeOperations;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TagParser.class)
public abstract class TagParserMixin<T> implements StringRangeTreeCreator<Tag>, AllowMalformedContainer, AnalyzingResultCreator {
    private @Nullable StringRangeTree.Builder<Tag> command_crafter$stringRangeTreeBuilder;
    private boolean command_crafter$allowMalformed = false;
    private @Nullable AnalyzingResult command_crafter$analyzingResult;

    @Override
    public void command_crafter$setAllowMalformed(boolean allowMalformed) {
        command_crafter$allowMalformed = allowMalformed;
    }

    @Override
    public boolean command_crafter$getAllowMalformed() {
        return command_crafter$allowMalformed;
    }

    @Override
    public void command_crafter$setStringRangeTreeBuilder(@NotNull StringRangeTree.Builder<Tag> builder) {
        command_crafter$stringRangeTreeBuilder = builder;
    }

    @Override
    public void command_crafter$setAnalyzingResult(AnalyzingResult analyzingResult) {
        this.command_crafter$analyzingResult = analyzingResult;
    }

    @WrapOperation(
            method = {"parseFully(Lcom/mojang/brigadier/StringReader;)Ljava/lang/Object;", "parseAsArgument"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/parsing/packrat/commands/Grammar;parseForCommands(Lcom/mojang/brigadier/StringReader;)Ljava/lang/Object;"
            )
    )
    private T command_crafter$setAdditionalPackratParserArgs(Grammar<@NotNull T> instance, StringReader reader, Operation<T> op) {
        var restoreArgsCallback = PackratParserAdditionalArgs.INSTANCE.temporarilyClearArgs();

        PackratParserAdditionalArgs.INSTANCE.getAllowMalformed().set(command_crafter$allowMalformed);
        StringRangeTree.PartialBuilder<Tag> partialStringRangeTreeBuilder = null;
        if(command_crafter$stringRangeTreeBuilder != null) {
            partialStringRangeTreeBuilder = new StringRangeTree.PartialBuilder<>();
            PackratParserAdditionalArgs.INSTANCE.getNbtStringRangeTreeBuilder().set(new PackratParserAdditionalArgs.StringRangeTreeBranchingArgument<>(partialStringRangeTreeBuilder));
        }
        if(command_crafter$analyzingResult != null) {
            PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult().set(new PackratParserAdditionalArgs.AnalyzingResultBranchingArgument(command_crafter$analyzingResult.copyInput()));
        }
        try {
            final var tag = op.call(instance, reader);
            if(command_crafter$analyzingResult != null) {
                PackratParserAdditionalArgs.INSTANCE.popAnalyzingResult(command_crafter$analyzingResult, null);
            }
            return tag;
        } finally {
            PackratParserAdditionalArgs.INSTANCE.getAllowMalformed().remove();
            PackratParserAdditionalArgs.INSTANCE.getNbtStringRangeTreeBuilder().remove();
            if(partialStringRangeTreeBuilder != null) {
                partialStringRangeTreeBuilder.addToBasicBuilder(command_crafter$stringRangeTreeBuilder);
            }
            PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult().remove();
            PackratParserAdditionalArgs.INSTANCE.getFurthestAnalyzingResult().remove();
            restoreArgsCallback.invoke();
        }
    }

    private static MalformedStringDecoderAnalyzing<DataObjectDecoding.EmbeddedNbtDecoderData<?>> command_crafter$decoderAnalyzing;

    @ModifyExpressionValue(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/codecs/PrimitiveCodec;comapFlatMap(Ljava/util/function/Function;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"
            ),
            remap = false
    )
    private static Codec<CompoundTag> command_crafter$storeFlattenedCodecInput(Codec<CompoundTag> codec) {
        command_crafter$decoderAnalyzing = new MalformedStringDecoderAnalyzing<>(
                (dynamic) -> DataObjectDecoding.Companion.getEmbeddedNbtDecoder(dynamic.getValue()),
                (decoderData, result, behavior, stringContent) -> {
                    final var nbtReader = TagParser.create(NbtOps.INSTANCE);
                    ((AllowMalformedContainer) nbtReader).command_crafter$setAllowMalformed(true);
                    ((AnalyzingResultCreator) nbtReader).command_crafter$setAnalyzingResult(result);
                    final var treeBuilder = new StringRangeTree.Builder<Tag>();
                    //noinspection unchecked
                    ((StringRangeTreeCreator<Tag>) nbtReader).command_crafter$setStringRangeTreeBuilder(treeBuilder);
                    Tag nbt;
                    try {
                        nbt = nbtReader.parseAsArgument(new StringReader(stringContent.getContent()));
                    } catch (CommandSyntaxException ignored) {
                        return; // Shouldn't happen
                    }

                    if (decoderData != null) {
                        final var tree = treeBuilder.build(nbt);
                        TreeOperations.Companion.forNbt(tree, stringContent.getContent())
                                .withDiagnosticSeverity(DiagnosticSeverity.Warning)
                                .withRegistry(behavior.getRegistries())
                                .withBranchBehaviorProvider(decoderData.getBranchBehaviorModifier().apply(BranchBehaviorProvider.Decode.INSTANCE))
                                .analyzeFull(result, decoderData.getDecoder());
                    }
                }
        );
        return command_crafter$decoderAnalyzing.wrapCodec(codec);
    }

    @ModifyReturnValue(
            method = "lambda$static$0",
            at = @At("RETURN:FIRST"),
            remap = false
    )
    private static DataResult<?> command_crafter$finishFlattenedCodecAnalyzingResult(DataResult<?> result, String s) {
        command_crafter$decoderAnalyzing.onParsed(result.isSuccess() ? Integer.MAX_VALUE : s.length(), null);
        return result;
    }

    @ModifyReturnValue(
            method = "lambda$static$0",
            at = @At("RETURN:LAST"),
            remap = false
    )
    private static DataResult<?> command_crafter$markFlattenedCodecSyntaxError(DataResult<?> result, String s, @Local CommandSyntaxException exception) {
        command_crafter$decoderAnalyzing.onParsed(exception.getCursor(), exception.getMessage());
        return result;
    }
}