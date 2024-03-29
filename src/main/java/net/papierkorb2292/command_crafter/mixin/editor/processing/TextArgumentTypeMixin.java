package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.TextArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingCommandNode;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultCreator;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

import java.io.IOException;

@Mixin(TextArgumentType.class)
public class TextArgumentTypeMixin implements AnalyzingCommandNode {

    @Override
    public void command_crafter$analyze(@NotNull CommandContext<ServerCommandSource> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> reader, @NotNull AnalyzingResult result, @NotNull String name) throws CommandSyntaxException {
        var readerCopy = reader.copy();
        readerCopy.setCursor(range.getStart());
        var jsonReader = new JsonReader(readerCopy.asReader());
        var prevReadCharacters = reader.getReadCharacters();
        try {
            reader.setReadCharacters(prevReadCharacters + range.getStart());
            //noinspection DataFlowIssue
            ((AnalyzingResultCreator)jsonReader).command_crafter$setAnalyzingResult(result);
            jsonReader.setLenient(false);
            new Gson().getAdapter(JsonElement.class).read(jsonReader);
        } catch (JsonParseException | IOException ignored) {
        } finally {
            reader.setReadCharacters(prevReadCharacters);
        }
    }
}
