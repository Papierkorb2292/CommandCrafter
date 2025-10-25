package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.command.argument.ItemPredicateParsing;
import net.minecraft.util.packrat.ParsingRuleEntry;
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net/minecraft/command/argument/ItemPredicateParsing$TagParsingRule")
public class TagParsingRuleMixin extends IdentifiableParsingRuleMixin {

    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    private void command_crafter$initIdAnalyzing(ParsingRuleEntry<?, ?> idParsingRule, ItemPredicateParsing.Callbacks<?, ?, ?> callbacks, CallbackInfo ci) {
        command_crafter$setStartOffset(-1);
        command_crafter$setPackContentFileType(PackContentFileType.ITEM_TAGS_FILE_TYPE);
    }
}
