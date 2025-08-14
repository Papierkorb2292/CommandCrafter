package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.command.argument.ItemStringReader;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingCommandNode;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultDataContainer;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ItemStackArgumentType.class)
public class ItemStackArgumentTypeMixin implements AnalyzingCommandNode {

    @Shadow @Final private ItemStringReader reader;

    @Override
    public void command_crafter$analyze(@NotNull CommandContext<CommandSource> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> stringReader, @NotNull AnalyzingResult result, @NotNull String name) throws CommandSyntaxException {
        try {
            ((AnalyzingResultDataContainer) reader).command_crafter$setAnalyzingResult(result);
            ((ItemStringReaderAccessor) reader).callConsume(stringReader);
        } finally {
            ((AnalyzingResultDataContainer) reader).command_crafter$setAnalyzingResult(null);
        }
    }
}
