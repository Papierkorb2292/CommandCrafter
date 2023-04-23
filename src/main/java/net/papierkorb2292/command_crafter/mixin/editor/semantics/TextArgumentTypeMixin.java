package net.papierkorb2292.command_crafter.mixin.editor.semantics;

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
import net.papierkorb2292.command_crafter.editor.processing.SemanticTokensBuilder;
import net.papierkorb2292.command_crafter.editor.processing.helper.SemanticCommandNode;
import net.papierkorb2292.command_crafter.editor.processing.helper.SemanticTokensCreator;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

import java.io.IOException;
import java.io.StringReader;

@Mixin(TextArgumentType.class)
public class TextArgumentTypeMixin implements SemanticCommandNode {

    @Override
    public void command_crafter$createSemanticTokens(@NotNull CommandContext<ServerCommandSource> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> reader, @NotNull SemanticTokensBuilder tokens) throws CommandSyntaxException {
        var jsonReader = new JsonReader(new StringReader(range.get(context.getInput())));
        ((SemanticTokensCreator)jsonReader).command_crafter$setSemanticTokensBuilder(tokens, reader.getReadCharacters() + range.getStart());
        jsonReader.setLenient(false);
        try {
            new Gson().getAdapter(JsonElement.class).read(jsonReader);
        } catch (JsonParseException | IOException ignored) { }
    }
}
