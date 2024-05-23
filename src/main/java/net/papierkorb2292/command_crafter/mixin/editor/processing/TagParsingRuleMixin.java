package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.command.argument.packrat.PackratParsing;
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net/minecraft/command/argument/packrat/PackratParsing$TagParsingRule")
public class TagParsingRuleMixin<T, C, P> extends IdentifiableParsingRuleMixin<PackratParsing.Callbacks<T, C, P>, T> {

    public TagParsingRuleMixin() {
        command_crafter$setStartOffset(-1);
        command_crafter$setPackContentFileType(PackContentFileType.ITEM_TAGS_FILE_TYPE);
    }
}
