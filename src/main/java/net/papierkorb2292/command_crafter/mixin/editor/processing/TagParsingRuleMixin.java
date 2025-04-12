package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net/minecraft/command/argument/ItemPredicateParsing$TagParsingRule")
public class TagParsingRuleMixin extends IdentifiableParsingRuleMixin {

    public TagParsingRuleMixin() {
        command_crafter$setStartOffset(-1);
        command_crafter$setPackContentFileType(PackContentFileType.ITEM_TAGS_FILE_TYPE);
    }
}
