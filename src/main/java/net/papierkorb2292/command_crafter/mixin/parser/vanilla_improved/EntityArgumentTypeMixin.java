package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import net.minecraft.command.EntitySelectorOptions;
import net.minecraft.command.EntitySelectorReader;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.RawZipResourceCreator;
import net.papierkorb2292.command_crafter.parser.helper.RawResource;
import net.papierkorb2292.command_crafter.parser.helper.UnparsableArgumentType;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.ArrayList;
import java.util.List;

@Mixin(EntityArgumentType.class)
public class EntityArgumentTypeMixin implements UnparsableArgumentType {
    
    @Nullable
    @Override
    public List<Either<String, RawResource>> command_crafter$unparseArgument(@NotNull CommandContext<ServerCommandSource> context, @NotNull String name, @NotNull DirectiveStringReader<RawZipResourceCreator> reader) throws CommandSyntaxException {
        int sectionStartCursor = reader.getCursor();
        if(!(VanillaLanguage.Companion.isReaderInlineResources(reader) && reader.canRead() && reader.read() == '@')) {
            return null;
        }
        reader.skip();
        if(!reader.canRead() || reader.read() != '[') {
            return null;
        }
        reader.skipWhitespace();
        List<Either<String, RawResource>> result = new ArrayList<>();
        var selectorReader = new EntitySelectorReader(reader);
        while(reader.canRead() && reader.peek() != ']') {
            reader.skipWhitespace();
            var argumentPosition = reader.getCursor();
            var argumentName = reader.readString();
            EntitySelectorOptions.SelectorHandler selectorHandler = EntitySelectorOptions.getHandler(selectorReader, argumentName, argumentPosition);
            reader.skipWhitespace();
            reader.expect('=');
            reader.skipWhitespace();
            if(argumentName.equals("type") && reader.canRead(2) && (reader.peek() == '(' || (reader.peek() == '!' && reader.peek(1) == '('))) {
                if(reader.peek() == '!') {
                    reader.skip();
                }
                result.add(Either.left(reader.getString().substring(sectionStartCursor, reader.getCursor()) + '#'));
                var entryList = VanillaLanguage.Companion.parseRawRegistryTagTuple(reader, Registries.ENTITY_TYPE);
                result.add(Either.right(entryList.getResource()));
                sectionStartCursor = reader.getCursor();
            } else {
                selectorHandler.handle(selectorReader);
            }
            reader.skipWhitespace();
            if (!reader.canRead() || reader.peek() != ',') {
                break;
            }
            reader.skip();
        }
        if(reader.canRead()) {
            reader.skip();
        }
        result.add(Either.left(reader.getString().substring(sectionStartCursor, reader.getCursor())));
        return result;
    }
}
