package net.papierkorb2292.command_crafter.mixin.packrat;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Cancellable;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtEnd;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.packrat.Parser;
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

@Mixin(targets = "net/minecraft/util/packrat/Parser$2")
public class ParserWithDecodingMixin<T> {

    @Shadow @Final
    DynamicOps<T> field_57540;

    @Shadow @Final
    Codec<?> field_57539;

    @WrapOperation(
            method = "parse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/packrat/Parser;parse(Lcom/mojang/brigadier/StringReader;)Ljava/lang/Object;"
            )
    )
    private T command_crafter$analyzeStringRangeTree(Parser<T> instance, StringReader reader, Operation<T> op) {
        // StringRangeTrees can only be build for nbt
        if(!(field_57540.empty() instanceof NbtElement))
            return op.call(instance, reader);

        if(!(reader instanceof DirectiveStringReader<?> directiveReader))
            return op.call(instance, reader);

        var analyzingResultThreadLocal = PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult();
        var analyzingResultArg = getOrNull(analyzingResultThreadLocal);
        if(analyzingResultArg == null)
            return op.call(instance, reader);

        var start = reader.getCursor();
        var analyzingResult = analyzingResultArg.getAnalyzingResult();
        analyzingResultThreadLocal.remove();
        var treeBuilder = new StringRangeTree.Builder<NbtElement>();
        NbtElement nbt;
        try {
            var partialBuilder = new StringRangeTree.PartialBuilder<NbtElement>();
            PackratParserAdditionalArgs.INSTANCE.getNbtStringRangeTreeBuilder().set(new PackratParserAdditionalArgs.StringRangeTreeBranchingArgument<>(partialBuilder));
            nbt = (NbtElement) MixinUtil.<T, CommandSyntaxException>callWithThrows(op, instance, reader);
            partialBuilder.addToBasicBuilder(treeBuilder);
        } catch(CommandSyntaxException e) {
            nbt = NbtEnd.INSTANCE;
            treeBuilder.addNode(nbt, new StringRange(start, reader.getCursor()), reader.getCursor());
        } finally {
            analyzingResultThreadLocal.set(analyzingResultArg);
            PackratParserAdditionalArgs.INSTANCE.getNbtStringRangeTreeBuilder().remove();
        }

        var tree = treeBuilder.build(nbt);
        StringRangeTree.TreeOperations.Companion.forNbt(
                tree,
                directiveReader
        ).analyzeFull(analyzingResult, true, field_57539);
        return (T)nbt;
    }

    @ModifyReceiver(
            method = "parse",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/DataResult;getOrThrow(Ljava/util/function/Function;)Ljava/lang/Object;",
                    remap = false
            )
    )
    private DataResult<RegistryEntry<T>> command_crafter$suppressDecoderErrorsWhenAnalyzing(DataResult<RegistryEntry<T>> original, Function<String, ?> stringEFunction, StringReader reader, @Cancellable CallbackInfoReturnable<RegistryEntry<T>> cir) {
        // Skip results with errors when analyzing, because decoder diagnostics are already generated through command_crafter$analyzeStringRangeTree
        // This also makes the analyzer more forgiving
        if(original.isError() && reader instanceof DirectiveStringReader<?> directiveStringReader && directiveStringReader.getResourceCreator() instanceof AnalyzingResourceCreator) {
            cir.setReturnValue(null);
        }
        return original;
    }
}
