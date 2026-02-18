package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import kotlin.Unit;
import net.minecraft.nbt.*;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.commands.TagParseRule;
import net.papierkorb2292.command_crafter.MixinUtil;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree;
import net.papierkorb2292.command_crafter.editor.processing.helper.AllowMalformedContainer;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultCreator;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import net.papierkorb2292.command_crafter.editor.processing.helper.StringRangeTreeCreator;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(TagParseRule.class)
public class TagParseRuleMixin<T> {

    @WrapOperation(
            method = "parse(Lnet/minecraft/util/parsing/packrat/ParseState;)Lcom/mojang/serialization/Dynamic;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/TagParser;parseAsArgument(Lcom/mojang/brigadier/StringReader;)Ljava/lang/Object;"
            )
    )
    private T command_crafter$analyzeNbt(TagParser<T> instance, StringReader reader, Operation<T> op, ParseState<StringReader> state) {
        final var analyzingResultArg = getOrNull(PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult());
        if (analyzingResultArg == null)
            return op.call(instance, reader);
        final var nbtReader = TagParser.create(NbtOps.INSTANCE);
        //noinspection unchecked
        var directiveReader = (DirectiveStringReader<AnalyzingResourceCreator>)state.input();
        var treeBuilder = new StringRangeTree.Builder<Tag>();
        //noinspection unchecked
        ((StringRangeTreeCreator<Tag>)nbtReader).command_crafter$setStringRangeTreeBuilder(treeBuilder);
        ((AllowMalformedContainer)nbtReader).command_crafter$setAllowMalformed(true);
        ((AnalyzingResultCreator)nbtReader).command_crafter$setAnalyzingResult(analyzingResultArg.getAnalyzingResult());
        final var startCursor = state.input().getCursor();
        T parsed = null;
        Tag nbt;
        try {
            parsed = MixinUtil.<T, CommandSyntaxException>callWithThrows(op, nbtReader, reader);
            if (!(parsed instanceof Tag))
                return parsed;
            nbt = (Tag) parsed;
        } catch(CommandSyntaxException e) {
            nbt = EndTag.INSTANCE;
            treeBuilder.addNode(EndTag.INSTANCE, new StringRange(startCursor, state.input().getCursor()), startCursor);
        }
        var tree = treeBuilder.build(nbt);
        var treeOps = StringRangeTree.TreeOperations.Companion.forNbt(
                tree,
                directiveReader
        );
        PackratParserAdditionalArgs.INSTANCE.getDelayedDecodeNbtAnalyzeCallback().set((ops, decoder) -> {
            // Instance might have changed in this time
            var newAnalyzingResultArg = getOrNull(PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult());
            if(newAnalyzingResultArg != null) {
                var registryTreeOps = treeOps.withOps(ops);
                registryTreeOps.analyzeFull(newAnalyzingResultArg.getAnalyzingResult(), decoder);
            }
            return Unit.INSTANCE;
        });
        return parsed;
    }
}
