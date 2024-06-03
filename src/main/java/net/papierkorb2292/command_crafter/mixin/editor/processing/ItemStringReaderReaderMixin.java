package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.mojang.brigadier.StringReader;
import net.minecraft.command.argument.ItemStringReader;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.StringNbtReader;
import net.papierkorb2292.command_crafter.editor.processing.NbtSemanticTokenProvider;
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree;
import net.papierkorb2292.command_crafter.editor.processing.TokenType;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultCreator;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultDataContainer;
import net.papierkorb2292.command_crafter.editor.processing.helper.StringRangeTreeCreator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.command.argument.ItemStringReader$Reader")
public class ItemStringReaderReaderMixin {

    @Final @Shadow
    ItemStringReader field_48970;

    @Shadow @Final private StringReader reader;
    private final AnalyzingResult command_crafter$analyzingResult = ((AnalyzingResultDataContainer)field_48970).command_crafter$getAnalyzingResult();

    @Inject(
            method = "readItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Identifier;fromCommandInput(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/util/Identifier;",
                    shift = At.Shift.AFTER
            )
    )
    private void command_crafter$highlightItemId(CallbackInfo ci, @Local int startCursor) {
        if(command_crafter$analyzingResult != null) {
            command_crafter$analyzingResult.getSemanticTokens().addMultiline(startCursor, reader.getCursor() - startCursor, TokenType.Companion.getPARAMETER(), 0);
        }
    }

    @Inject(
            method = "readComponents",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/argument/ItemStringReader$Reader;readComponentType(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/component/DataComponentType;"
            )
    )
    private void command_crafter$saveComponentTypeStart(CallbackInfo ci, @Share("componentTypeStart") LocalIntRef startCursor) {
        startCursor.set(reader.getCursor());
    }

    @Inject(
            method = "readComponents",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/argument/ItemStringReader$Reader;readComponentType(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/component/DataComponentType;",
                    shift = At.Shift.AFTER
            )
    )
    private void command_crafter$highlightComponentId(CallbackInfo ci, @Share("componentTypeStart") LocalIntRef startCursor) {
        if(command_crafter$analyzingResult != null) {
            command_crafter$analyzingResult.getSemanticTokens().addMultiline(startCursor.get(), reader.getCursor() - startCursor.get(), TokenType.Companion.getPARAMETER(), 0);
        }
    }

    @WrapOperation(
            method = "readComponentValue",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/StringNbtReader;parseElement()Lnet/minecraft/nbt/NbtElement;"
            )
    )
    private NbtElement command_crafter$analyzeComponentNbt(StringNbtReader nbtReader, Operation<NbtElement> op) {
        if (command_crafter$analyzingResult == null) {
            return op.call(nbtReader);
        }
        var treeBuilder = new StringRangeTree.Builder<NbtElement>();
        //noinspection unchecked
        ((StringRangeTreeCreator<NbtElement>)nbtReader).command_crafter$setStringRangeTreeBuilder(treeBuilder);
        var nbt = op.call(nbtReader);
        var tree = treeBuilder.build(nbt);
        tree.generateSemanticTokens(new NbtSemanticTokenProvider(tree), command_crafter$analyzingResult.getSemanticTokens());
        return nbt;
    }
}
