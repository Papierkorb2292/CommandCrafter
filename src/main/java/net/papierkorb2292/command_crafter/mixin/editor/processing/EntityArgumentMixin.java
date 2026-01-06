package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.commands.arguments.EntityArgument;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.helper.AllowMalformedContainer;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingCommandNode;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultDataContainer;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(EntityArgument.class)
public class EntityArgumentMixin implements AnalyzingCommandNode {

    @Override
    public void command_crafter$analyze(@NotNull CommandContext<SharedSuggestionProvider> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> reader, @NotNull AnalyzingResult result, @NotNull String name) throws CommandSyntaxException {
        var selectorReader = new EntitySelectorParser(reader, true);
        ((AnalyzingResultDataContainer)selectorReader).command_crafter$setAnalyzingResult(result);
        ((AllowMalformedContainer)selectorReader).command_crafter$setAllowMalformed(true);
        selectorReader.parse();
    }

    @ModifyExpressionValue(
            method = "listSuggestions",
            at = @At(
                    value = "NEW",
                    target = "(Lcom/mojang/brigadier/StringReader;Z)Lnet/minecraft/commands/arguments/selector/EntitySelectorParser;"
            )
    )
    private static EntitySelectorParser command_crafter$allowMalformedSuggestions(EntitySelectorParser original) {
        if(getOrNull(VanillaLanguage.Companion.getSUGGESTIONS_FULL_INPUT()) != null)
            ((AllowMalformedContainer) original).command_crafter$setAllowMalformed(true);
        return original;
    }

    @Inject(
            method = "parse(Lcom/mojang/brigadier/StringReader;Z)Lnet/minecraft/commands/arguments/selector/EntitySelector;",
            at = @At("HEAD")
    )
    private void command_crafter$saveCursorPosition(StringReader stringReader, boolean bl, CallbackInfoReturnable<EntitySelector> cir, @Share("startCursor") LocalIntRef startPos) {
        startPos.set(stringReader.getCursor());
    }

    @ModifyArg(
            method = "parse(Lcom/mojang/brigadier/StringReader;Z)Lnet/minecraft/commands/arguments/selector/EntitySelector;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;setCursor(I)V"
            )
    )
    private int command_crafter$fixMC272429(int cursor, @Share("startCursor") LocalIntRef startPos) {
        // Minecraft would reset the cursor all the way to zero, which leads to bad error messages
        return cursor == 0 ? startPos.get() : cursor;
    }
}
