package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.StringRange;
import net.minecraft.command.argument.packrat.IdentifiableParsingRule;
import net.minecraft.command.argument.packrat.ParsingState;
import net.minecraft.util.Identifier;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.IdArgumentTypeAnalyzer;
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType;
import net.papierkorb2292.command_crafter.editor.processing.TokenType;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackContentFileTypeContainer;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAnalyzingResult;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(IdentifiableParsingRule.class)
public class IdentifiableParsingRuleMixin<C, V> implements PackContentFileTypeContainer {

    private PackContentFileType command_crafter$packContentFileType = null;
    private int startOffset = 0;

    @Inject(
            method = "parse(Lnet/minecraft/command/argument/packrat/ParsingState;)Ljava/util/Optional;",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Optional;of(Ljava/lang/Object;)Ljava/util/Optional;"
            )
    )
    private void command_crafter$analyzeId(ParsingState<StringReader> state, CallbackInfoReturnable<Optional<V>> cir, @Local int start, @SuppressWarnings("OptionalUsedAsFieldOrParameterType") @Local Optional<Identifier> id) {
        var analyzingResult = getOrNull(PackratParserAnalyzingResult.INSTANCE.getAnalyzingResult());
        var offsetStart = start + startOffset;
        var stringReader = state.getReader();
        if(analyzingResult != null && stringReader instanceof DirectiveStringReader<?> directiveStringReader && directiveStringReader.getResourceCreator() instanceof AnalyzingResourceCreator) {
            var range = new StringRange(offsetStart, state.getCursor());
            if (command_crafter$packContentFileType != null) {
                //noinspection OptionalGetWithoutIsPresent,unchecked
                IdArgumentTypeAnalyzer.INSTANCE.analyzeForId(id.get(), command_crafter$packContentFileType, range, analyzingResult, (DirectiveStringReader<AnalyzingResourceCreator>) directiveStringReader);
                return;
            }
            analyzingResult.getSemanticTokens().addMultiline(range, TokenType.Companion.getPARAMETER(), 0);
        }
    }

    protected void command_crafter$setStartOffset(int startOffset) {
        this.startOffset = startOffset;
    }

    @Override
    public void command_crafter$setPackContentFileType(@NotNull PackContentFileType packContentFileType) {
        command_crafter$packContentFileType = packContentFileType;
    }

    @Nullable
    @Override
    public PackContentFileType command_crafter$getPackContentFileType() {
        return command_crafter$packContentFileType;
    }
}
