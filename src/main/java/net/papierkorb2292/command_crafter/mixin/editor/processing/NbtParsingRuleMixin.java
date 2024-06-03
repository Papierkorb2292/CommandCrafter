package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.command.argument.packrat.NbtParsingRule;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.StringNbtReader;
import net.papierkorb2292.command_crafter.editor.processing.NbtSemanticTokenProvider;
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultCreator;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import net.papierkorb2292.command_crafter.editor.processing.helper.StringRangeTreeCreator;
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
    private NbtElement command_crafter$analyzeNbt(StringNbtReader stringNbtReader, Operation<NbtElement> op) {
        var analyzingResult = getOrNull(PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult());
        if (analyzingResult == null)
            return op.call(stringNbtReader);
        var treeBuilder = new StringRangeTree.Builder<NbtElement>();
        //noinspection unchecked
        ((StringRangeTreeCreator<NbtElement>)stringNbtReader).command_crafter$setStringRangeTreeBuilder(treeBuilder);
        var nbt = op.call(stringNbtReader);
        var tree = treeBuilder.build(nbt);
        tree.generateSemanticTokens(new NbtSemanticTokenProvider(tree), analyzingResult.getSemanticTokens());
        return nbt;
    }
}
