package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.parsing.packrat.commands.Grammar;
import net.papierkorb2292.command_crafter.editor.processing.DataObjectDecoding;
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree;
import net.papierkorb2292.command_crafter.editor.processing.codecmod.ExtraDecoderBehavior;
import net.papierkorb2292.command_crafter.editor.processing.helper.*;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    private static ThreadLocal<Dynamic<?>> command_crafter$flattenedCodecInput = new ThreadLocal<>();

    @ModifyExpressionValue(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/codecs/PrimitiveCodec;comapFlatMap(Ljava/util/function/Function;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"
            ),
            remap = false
    )
    private static Codec<CompoundTag> command_crafter$storeFlattenedCodecInput(Codec<CompoundTag> codec) {
        return new Codec<>() {
            @Override
            public <U> DataResult<Pair<CompoundTag, U>> decode(DynamicOps<U> ops, U input) {
                final var prevStringErrorInput = command_crafter$flattenedCodecInput.get();
                command_crafter$flattenedCodecInput.set(new Dynamic<>(ops, input));
                var result = codec.decode(ops, input);
                if(prevStringErrorInput != null)
                    command_crafter$flattenedCodecInput.set(prevStringErrorInput);
                else
                    command_crafter$flattenedCodecInput.remove();
                return result;
            }

            @Override
            public <U> DataResult<U> encode(CompoundTag input, DynamicOps<U> ops, U prefix) {
                return codec.encode(input, ops, prefix);
            }
        };
    }

    @Inject(
            method = "lambda$static$0",
            at = @At("HEAD")
    )
    private static void command_crafter$analyzeFlattenedNbt(String input, CallbackInfoReturnable<DataResult<CompoundTag>> cir, @Share("analyzingResult") LocalRef<AnalyzingResult> analyzingResult, @Share("stringContent") LocalRef<StringRangeTree.StringContent> stringContentRef) {
        final var dynamic = command_crafter$flattenedCodecInput.get();
        command_crafter$analyzeFlattenedNbtGeneric(dynamic, analyzingResult, stringContentRef);
    }

    private static <T> void command_crafter$analyzeFlattenedNbtGeneric(Dynamic<T> dynamic, LocalRef<AnalyzingResult> analyzingResult, LocalRef<StringRangeTree.StringContent> stringContentRef) {
        final var extraBehavior = ExtraDecoderBehavior.Companion.getCurrentBehavior(dynamic.getOps());
        if(extraBehavior == null || extraBehavior.getNodeAnalyzingBehavior() == null)
            return;
        final var analyzingBehavior = extraBehavior.getNodeAnalyzingBehavior();
        final var stringContent = analyzingBehavior.getStringContentGetter().getStringContent(dynamic.getValue());
        if(stringContent == null)
            return;
        stringContentRef.set(stringContent);
        analyzingResult.set(analyzingBehavior.createStringAnalyzingResultOverlay(dynamic.getValue(), stringContent));

        final var nbtReader = TagParser.create(NbtOps.INSTANCE);
        ((AllowMalformedContainer)nbtReader).command_crafter$setAllowMalformed(true);
        ((AnalyzingResultCreator)nbtReader).command_crafter$setAnalyzingResult(analyzingResult.get());
        final var treeBuilder = new StringRangeTree.Builder<Tag>();
        //noinspection unchecked
        ((StringRangeTreeCreator<Tag>)nbtReader).command_crafter$setStringRangeTreeBuilder(treeBuilder);
        Tag nbt;
        try {
            nbt = nbtReader.parseAsArgument(new StringReader(stringContent.getContent()));
        } catch (CommandSyntaxException ignored) {
            return; // Shouldn't happen
        }
        final var tree = treeBuilder.build(nbt);

        final var decoderData = DataObjectDecoding.Companion.getEmbeddedNbtDecoder(dynamic.getValue());
        if (decoderData != null) {
            final var treeOps = StringRangeTree.TreeOperations.Companion.forNbt(tree, stringContent.getContent())
                    .withDiagnosticSeverity(DiagnosticSeverity.Warning)
                    .withRegistry(extraBehavior.getRegistries());
            if(decoderData.getBranchBehaviorOverride() != null)
                treeOps.withBranchBehaviorProvider(decoderData.getBranchBehaviorOverride());
            treeOps.analyzeFull(analyzingResult.get(), decoderData.getDecoder());
        }
    }

    @ModifyReturnValue(
            method = "lambda$static$0",
            at = @At("RETURN:FIRST"),
            remap = false
    )
    private static DataResult<?> command_crafter$finishFlattenedCodecAnalyzingResult(DataResult<?> result, String s, @Share("analyzingResult") LocalRef<AnalyzingResult> analyzingResult, @Share("stringContent") LocalRef<StringRangeTree.StringContent> stringContent) {
        final var dynamic = command_crafter$flattenedCodecInput.get();
        command_crafter$finishFlattenedCodecAnalyzingResultGeneric(dynamic, result.isSuccess() ? Integer.MAX_VALUE : s.length(), analyzingResult, stringContent);
        return result;
    }

    private static <T> void command_crafter$finishFlattenedCodecAnalyzingResultGeneric(Dynamic<T> dynamic, int cursor, LocalRef<AnalyzingResult> analyzingResult, LocalRef<StringRangeTree.StringContent> stringContent) {
        final var extraBehavior = ExtraDecoderBehavior.Companion.getCurrentBehavior(dynamic.getOps());
        if(extraBehavior != null && extraBehavior.getNodeAnalyzingBehavior() != null) {
            extraBehavior.getNodeAnalyzingBehavior().finishNodeAnalyzingResultOverlay(
                    dynamic.getValue(),
                    analyzingResult.get(),
                    cursor,
                    stringContent.get()
            );
        }
    }

    @ModifyReturnValue(
            method = "lambda$static$0",
            at = @At("RETURN:LAST"),
            remap = false
    )
    private static DataResult<?> command_crafter$markFlattenedCodecSyntaxError(DataResult<?> result, String s, @Local CommandSyntaxException exception, @Share("analyzingResult") LocalRef<AnalyzingResult> analyzingResultRef, @Share("stringContent") LocalRef<StringRangeTree.StringContent> stringContent) {
        final var dynamic = command_crafter$flattenedCodecInput.get();
        command_crafter$markFlattenedCodecSyntaxErrorGeneric(dynamic, s, exception, analyzingResultRef, stringContent);
        return result;
    }

    private static <T> void command_crafter$markFlattenedCodecSyntaxErrorGeneric(Dynamic<T> dynamic, String s, CommandSyntaxException exception, LocalRef<AnalyzingResult> analyzingResultRef, LocalRef<StringRangeTree.StringContent> stringContent) {
        final var extraBehavior = ExtraDecoderBehavior.Companion.getCurrentBehavior(dynamic.getOps());
        if(extraBehavior == null)
            return;
        extraBehavior.markStringParseError(dynamic.getValue());
        if(extraBehavior.getNodeAnalyzingBehavior() != null) {
            final var analyzingResult = analyzingResultRef.get();
            final var mappingInfo = analyzingResult.getMappingInfo();
            final var diagnostic = new Diagnostic();
            diagnostic.setRange(new Range(
                    AnalyzingResult.Companion.getPositionFromCursor(mappingInfo.getCursorMapper().mapToSource(exception.getCursor() + mappingInfo.getReadSkippingChars(), false), mappingInfo, true),
                    AnalyzingResult.Companion.getPositionFromCursor(mappingInfo.getCursorMapper().mapToSource(s.length() + mappingInfo.getReadSkippingChars(), false), mappingInfo, true)
            ));
            diagnostic.setMessage(exception.getMessage());
            diagnostic.setSeverity(DiagnosticSeverity.Error);
            analyzingResult.getDiagnostics().add(diagnostic);
            extraBehavior.getNodeAnalyzingBehavior().finishNodeAnalyzingResultOverlay(dynamic.getValue(), analyzingResult, exception.getCursor(), stringContent.get());
        }
    }
}