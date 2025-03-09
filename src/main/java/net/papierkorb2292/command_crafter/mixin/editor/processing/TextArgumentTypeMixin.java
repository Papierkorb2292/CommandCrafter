package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.TextArgumentType;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.TextCodecs;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree;
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTreeJsonReader;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingCommandNode;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.string_range_gson.Strictness;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(TextArgumentType.class)
public class TextArgumentTypeMixin implements AnalyzingCommandNode {

    @Shadow @Final private RegistryWrapper.WrapperLookup registries;

    @Override
    public void command_crafter$analyze(@NotNull CommandContext<CommandSource> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> reader, @NotNull AnalyzingResult result, @NotNull String name) throws CommandSyntaxException {
        var readerCopy = reader.copy();

        readerCopy.setCursor(range.getStart());
        var stringRangeTreeReader = new StringRangeTreeJsonReader(readerCopy.asReader());
        var prevReadCharacters = result.getMappingInfo().getReadCharacters();
        try {
            result.getMappingInfo().setReadCharacters(prevReadCharacters + range.getStart());
            var stringRangeTree = stringRangeTreeReader.read(Strictness.LENIENT, true);
            readerCopy.setCursor(range.getStart());
            StringRangeTree.TreeOperations.Companion.forJson(
                    stringRangeTree,
                    readerCopy
            ).withRegistry(registries)
                    .analyzeFull(result, true, TextCodecs.CODEC);
        } finally {
            result.getMappingInfo().setReadCharacters(prevReadCharacters);
        }
    }
}
