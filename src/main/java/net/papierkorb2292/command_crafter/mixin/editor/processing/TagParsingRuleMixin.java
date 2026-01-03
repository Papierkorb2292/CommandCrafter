package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.commands.arguments.item.ComponentPredicateParser;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.parsing.packrat.NamedRule;
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.commands.arguments.item.ComponentPredicateParser$TagLookupRule")
public class TagParsingRuleMixin extends ResourceLookupRuleMixin {

    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    private void command_crafter$initIdAnalyzing(NamedRule<?, ?> idParsingRule, ComponentPredicateParser.Context<?, ?, ?> callbacks, CallbackInfo ci) {
        command_crafter$setStartOffset(-1);
        command_crafter$setPackContentFileType(PackContentFileType.Companion.getOrCreateTypeForRegistryTag(Registries.ITEM));
    }
}
