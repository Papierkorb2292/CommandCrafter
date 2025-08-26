package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import kotlin.Unit;
import net.minecraft.nbt.*;
import net.minecraft.util.packrat.ParsingState;
import net.papierkorb2292.command_crafter.MixinUtil;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree;
import net.papierkorb2292.command_crafter.editor.processing.helper.AllowMalformedContainer;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import net.papierkorb2292.command_crafter.editor.processing.helper.StringRangeTreeCreator;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Objects;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(NbtParsingRule.class)
public class NbtParsingRuleMixin<T> {

    @WrapOperation(
            method = "parse(Lnet/minecraft/util/packrat/ParsingState;)Lcom/mojang/serialization/Dynamic;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/StringNbtReader;readAsArgument(Lcom/mojang/brigadier/StringReader;)Ljava/lang/Object;"
            )
    )
    private T command_crafter$analyzeNbt(StringNbtReader<T> instance, StringReader reader, Operation<T> op, ParsingState<StringReader> state) {
        // Only get analyzing result to check whether analyzing should happen. The actual analyzing result to use is only retrieved later in the callback,
        // at which point it might be a different instance.
        if (getOrNull(PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult()) == null)
            return op.call(instance, reader);
        final var nbtReader = StringNbtReader.fromOps(NbtOps.INSTANCE);
        //noinspection unchecked
        var directiveReader = (DirectiveStringReader<AnalyzingResourceCreator>)state.getReader();
        var treeBuilder = new StringRangeTree.Builder<NbtElement>();
        //noinspection unchecked
        ((StringRangeTreeCreator<NbtElement>)nbtReader).command_crafter$setStringRangeTreeBuilder(treeBuilder);
        ((AllowMalformedContainer)nbtReader).command_crafter$setAllowMalformed(true);
        final var startCursor = state.getReader().getCursor();
        T parsed = null;
        NbtElement nbt;
        try {
            parsed = MixinUtil.<T, CommandSyntaxException>callWithThrows(op, nbtReader, reader);
            if (!(parsed instanceof NbtElement))
                return parsed;
            nbt = (NbtElement) parsed;
        } catch(CommandSyntaxException e) {
            nbt = NbtEnd.INSTANCE;
            treeBuilder.addNode(NbtEnd.INSTANCE, new StringRange(startCursor, state.getReader().getCursor()), startCursor);
        }
        var tree = treeBuilder.build(nbt);
        var treeOps = StringRangeTree.TreeOperations.Companion.forNbt(
                tree,
                directiveReader
        );
        PackratParserAdditionalArgs.INSTANCE.getDelayedDecodeNbtAnalyzeCallback().set((ops, decoder) -> {
            var analyzingResultArg = getOrNull(PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult());
            if(analyzingResultArg != null) {
                var registryTreeOps = treeOps.withOps(ops);
                registryTreeOps.analyzeFull(analyzingResultArg.getAnalyzingResult(), true, decoder);
            }
            return Unit.INSTANCE;
        });
        return parsed;
    }
}
