package net.papierkorb2292.command_crafter.mixin.editor.semantics;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.mojang.brigadier.StringReader;
import net.minecraft.command.argument.ItemStringReader;
import net.minecraft.nbt.StringNbtReader;
import net.papierkorb2292.command_crafter.editor.processing.TokenType;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultCreator;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStringReader.class)
public class ItemStringReaderMixin implements AnalyzingResultCreator {

    @Shadow @Final private StringReader reader;
    private AnalyzingResult command_crafter$analyzingResult = null;

    @Override
    public void command_crafter$setAnalyzingResult(@NotNull AnalyzingResult result) {
        command_crafter$analyzingResult = result;
    }

    @Inject(
            method = "consume",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;canRead()Z",
                    ordinal = 0,
                    remap = false
            )
    )
    private void command_crafter$storeIdCursor(CallbackInfo ci, @Share("startCursor") LocalIntRef startCursor) {
        startCursor.set(reader.getCursor());
    }

    @Inject(
            method = "consume",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;canRead()Z",
                    ordinal = 1,
                    remap = false
            )
    )
    private void command_crafter$highlightId(CallbackInfo ci, @Share("startCursor") LocalIntRef startCursor) {
        if (command_crafter$analyzingResult != null) {
            command_crafter$analyzingResult.getSemanticTokens().addAbsoluteMultiline(startCursor.get(), reader.getCursor(), TokenType.Companion.getPARAMETER(), 0);
        }
    }

    @ModifyReceiver(
            method = "readNbt",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/StringNbtReader;parseCompound()Lnet/minecraft/nbt/NbtCompound;"
            )
    )
    private StringNbtReader command_crafter$analyzeNbt(StringNbtReader nbtReader) {
        if (command_crafter$analyzingResult != null) {
            ((AnalyzingResultCreator)nbtReader).command_crafter$setAnalyzingResult(command_crafter$analyzingResult);
        }
        return nbtReader;
    }
}
