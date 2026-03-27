package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import kotlin.Unit;
import net.minecraft.nbt.*;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.commands.TagParseRule;
import net.papierkorb2292.command_crafter.MixinUtil;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.MalformedParseErrorList;
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree;
import net.papierkorb2292.command_crafter.editor.processing.helper.AllowMalformedContainer;
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
        if (!PackratParserAdditionalArgs.INSTANCE.shouldAllowMalformed())
            return op.call(instance, reader);
        final var nbtReader = TagParser.create(NbtOps.INSTANCE);
        var treeBuilder = new StringRangeTree.Builder<Tag>();
        //noinspection unchecked
        ((StringRangeTreeCreator<Tag>)nbtReader).command_crafter$setStringRangeTreeBuilder(treeBuilder);
        ((AllowMalformedContainer)nbtReader).command_crafter$setAllowMalformed(true);
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
        if(state.errorCollector() instanceof MalformedParseErrorList<StringReader> malformedParseErrorList) {
            // Check if the nbt was ended correctly (otherwise don't give other suggestions)
            if (nbt instanceof EndTag)
                malformedParseErrorList.setLastMalformedEndCursor(reader.getCursor());
            else if (nbt instanceof CompoundTag || nbt instanceof CollectionTag) {
                if (nbt instanceof CompoundTag && reader.peek(-1) != '}') {
                    malformedParseErrorList.setLastMalformedEndCursor(reader.getCursor());
                } else if (nbt instanceof CollectionTag && reader.peek(-1) != ']') {
                    malformedParseErrorList.setLastMalformedEndCursor(reader.getCursor());
                } else if (tree.getRanges().values().stream().filter(range -> range.getEnd() == reader.getCursor()).count() > 1) {
                    // A child compound/list ended here
                    malformedParseErrorList.setLastMalformedEndCursor(reader.getCursor());
                }
            }
        }
        PackratParserAdditionalArgs.INSTANCE.getDelayedDecodeNbtAnalyzeCallback().set((ops, decoder) -> {
            var analyzingResultArg = getOrNull(PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult());
            if(analyzingResultArg != null) {
                //noinspection unchecked
                var directiveReader = (DirectiveStringReader<AnalyzingResourceCreator>)state.input();
                var treeOps = StringRangeTree.TreeOperations.Companion.forNbt(tree, directiveReader);
                var registryTreeOps = treeOps.withOps(ops);
                registryTreeOps.analyzeFull(analyzingResultArg.getAnalyzingResult(), true, decoder);
            }
            return Unit.INSTANCE;
        });
        return parsed;
    }
}
