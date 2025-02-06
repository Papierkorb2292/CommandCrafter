package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import kotlin.Unit;
import net.minecraft.command.argument.packrat.NbtParsingRule;
import net.minecraft.command.argument.packrat.ParsingState;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtEnd;
import net.minecraft.nbt.StringNbtReader;
import net.papierkorb2292.command_crafter.MixinUtil;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree;
import net.papierkorb2292.command_crafter.editor.processing.helper.AllowMalformedContainer;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import net.papierkorb2292.command_crafter.editor.processing.helper.StringRangeTreeCreator;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(NbtParsingRule.class)
public class NbtParsingRuleMixin {

    @WrapOperation(
            method = "parse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/StringNbtReader;parseElement()Lnet/minecraft/nbt/NbtElement;"
            )
    )
    private NbtElement command_crafter$analyzeNbt(StringNbtReader stringNbtReader, Operation<NbtElement> op, ParsingState<StringReader> state) {
        // Only get analyzing result to check whether analyzing should happen. The actual analyzing result to use is only retrieved later in the callback,
        // at which point it might be a different instance.
        if (getOrNull(PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult()) == null)
            return op.call(stringNbtReader);
        //noinspection unchecked
        var directiveReader = (DirectiveStringReader<AnalyzingResourceCreator>)state.getReader();
        if(directiveReader.getResourceCreator().getLanguageServer() == null)
            return op.call(stringNbtReader);
        var languageServer = directiveReader.getResourceCreator().getLanguageServer();
        var treeBuilder = new StringRangeTree.Builder<NbtElement>();
        //noinspection unchecked
        ((StringRangeTreeCreator<NbtElement>)stringNbtReader).command_crafter$setStringRangeTreeBuilder(treeBuilder);
        ((AllowMalformedContainer)stringNbtReader).command_crafter$setAllowMalformed(true);
        final var startCursor = state.getReader().getCursor();
        NbtElement nbt;
        try {
            nbt = MixinUtil.<NbtElement, CommandSyntaxException>callWithThrows(op, stringNbtReader);
        } catch(CommandSyntaxException e) {
            nbt = NbtEnd.INSTANCE;
            treeBuilder.addNode(nbt, new StringRange(startCursor, state.getReader().getCursor()), startCursor);
        }
        var tree = treeBuilder.build(nbt);
        var treeOps = StringRangeTree.TreeOperations.Companion.forNbt(
                tree,
                directiveReader
        );
        PackratParserAdditionalArgs.INSTANCE.getDelayedDecodeNbtAnalyzeCallback().set((ops, decoder) -> {
            var analyzingResult = getOrNull(PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult());
            if(analyzingResult != null) {
                treeOps.withOps(ops)
                        .analyzeFull(analyzingResult, languageServer, true, decoder);
            }
            return Unit.INSTANCE;
        });
        return nbt;
    }
}
