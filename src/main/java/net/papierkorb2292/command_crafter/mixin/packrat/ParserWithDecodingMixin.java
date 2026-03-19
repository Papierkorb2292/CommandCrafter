package net.papierkorb2292.command_crafter.mixin.packrat;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Cancellable;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.EndTag;
import net.minecraft.core.Holder;
import net.minecraft.util.parsing.packrat.commands.CommandArgumentParser;
import net.papierkorb2292.command_crafter.MixinUtil;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(targets = "net.minecraft.util.parsing.packrat.commands.CommandArgumentParser$2")
public class ParserWithDecodingMixin<T> {

    @Shadow @Final
    DynamicOps<T> val$ops;

    @Shadow @Final
    Codec<?> val$codec;

    @WrapOperation(
            method = "parseForCommands",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/parsing/packrat/commands/CommandArgumentParser;parseForCommands(Lcom/mojang/brigadier/StringReader;)Ljava/lang/Object;"
            )
    )
    private T command_crafter$analyzeStringRangeTree(CommandArgumentParser<T> instance, StringReader reader, Operation<T> op) {
        // StringRangeTrees can only be build for nbt
        if(!(val$ops.empty() instanceof Tag))
            return op.call(instance, reader);

        if(!(reader instanceof DirectiveStringReader<?> directiveReader))
            return op.call(instance, reader);

        var analyzingResultThreadLocal = PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult();
        var analyzingResultArg = getOrNull(analyzingResultThreadLocal);
        if(analyzingResultArg == null)
            return op.call(instance, reader);

        var start = reader.getCursor();
        var analyzingResult = analyzingResultArg.getAnalyzingResult();
        var treeBuilder = new StringRangeTree.Builder<Tag>();
        Tag nbt;
        try {
            analyzingResultThreadLocal.set(new PackratParserAdditionalArgs.AnalyzingResultBranchingArgument(analyzingResult.copyInput()));
            try {
                var partialBuilder = new StringRangeTree.PartialBuilder<Tag>();
                PackratParserAdditionalArgs.INSTANCE.getNbtStringRangeTreeBuilder().set(new PackratParserAdditionalArgs.StringRangeTreeBranchingArgument<>(partialBuilder));
                nbt = (Tag) MixinUtil.<T, CommandSyntaxException>callWithThrows(op, instance, reader);
                partialBuilder.addToBasicBuilder(treeBuilder);
                PackratParserAdditionalArgs.INSTANCE.popAnalyzingResult(analyzingResult, null);
            } catch (CommandSyntaxException e) {
                nbt = EndTag.INSTANCE;
                treeBuilder.addNode(nbt, new StringRange(start, reader.getCursor()), reader.getCursor());
            } finally {
                PackratParserAdditionalArgs.INSTANCE.getNbtStringRangeTreeBuilder().remove();
                analyzingResultThreadLocal.remove();
                PackratParserAdditionalArgs.INSTANCE.getFurthestAnalyzingResult().remove();
            }

            var tree = treeBuilder.build(nbt);
            StringRangeTree.TreeOperations.Companion.forNbt(
                    tree,
                    directiveReader
            ).analyzeFull(analyzingResult, val$codec);
        } finally {
            analyzingResultThreadLocal.set(analyzingResultArg);
        }
        return (T)nbt;
    }

    @WrapOperation(
            method = "parseForCommands",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/parsing/packrat/commands/CommandArgumentParser;parseForCommands(Lcom/mojang/brigadier/StringReader;)Ljava/lang/Object;"
            )
    )
    private T command_crafter$stringifyNbt(CommandArgumentParser<T> instance, StringReader reader, Operation<T> op) {
        if (!(val$ops.empty() instanceof Tag))
            return op.call(instance, reader);

        final var stringifiedArgument = getOrNull(PackratParserAdditionalArgs.INSTANCE.getStringifiedArgument());
        PackratParserAdditionalArgs.INSTANCE.getStringifiedArgument().remove();
        final var nbt =  op.call(instance, reader);
        if(stringifiedArgument != null) {
            stringifiedArgument.getStringified().add(Either.left(nbt.toString()));
            PackratParserAdditionalArgs.INSTANCE.getStringifiedArgument().set(stringifiedArgument);
        }
        return nbt;
    }

    @ModifyReceiver(
            method = "parseForCommands",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/DataResult;getOrThrow(Ljava/util/function/Function;)Ljava/lang/Object;",
                    remap = false
            )
    )
    private DataResult<Holder<T>> command_crafter$suppressDecoderErrorsWhenAnalyzing(DataResult<Holder<T>> original, Function<String, ?> stringEFunction, StringReader reader, @Cancellable CallbackInfoReturnable<Holder<T>> cir) {
        // Skip results with errors when analyzing, because decoder diagnostics are already generated through command_crafter$analyzeStringRangeTree
        // This also makes the analyzer more forgiving
        if(original.isError() && reader instanceof DirectiveStringReader<?> directiveStringReader && directiveStringReader.getResourceCreator() instanceof AnalyzingResourceCreator) {
            cir.setReturnValue(Holder.direct(null)); // Don't use return value "null", because Kotlin might expect a non-null value
        }
        return original;
    }
}
