package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.TagParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.commands.CommandSourceStack;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.RawZipResourceCreator;
import net.papierkorb2292.command_crafter.parser.helper.RawResource;
import net.papierkorb2292.command_crafter.parser.helper.StringifiableArgumentType;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.ArrayList;
import java.util.List;

@Mixin(EntityArgument.class)
public class EntityArgumentMixin implements StringifiableArgumentType {
    
    @Nullable
    @Override
    public List<Either<String, RawResource>> command_crafter$stringifyArgument(@NotNull CommandContext<CommandSourceStack> context, @NotNull String name, @NotNull DirectiveStringReader<RawZipResourceCreator> reader) throws CommandSyntaxException {
        int sectionStartCursor = reader.getCursor();
        if(!(VanillaLanguage.Companion.isReaderInlineResources(reader) && reader.canRead() && reader.read() == '@')) {
            return null;
        }
        reader.skip();
        if(!reader.canRead() || reader.read() != '[') {
            return null;
        }
        List<Either<String, RawResource>> result = new ArrayList<>();
        result.add(Either.left(reader.getString().substring(sectionStartCursor, reader.getCursor())));
        var selectorReader = new EntitySelectorParser(reader, true);
        var isFirstArgument = true;
        while(reader.canRead() && reader.peek() != ']') {
            reader.skipWhitespace();
            var argumentPosition = sectionStartCursor = reader.getCursor();
            var argumentName = reader.readString();
            if (isFirstArgument) isFirstArgument = false;
            else result.add(Either.left(","));
            result.add(Either.left(reader.getString().substring(sectionStartCursor, reader.getCursor())));
            EntitySelectorOptions.Modifier selectorHandler = EntitySelectorOptions.get(selectorReader, argumentName, argumentPosition);
            reader.skipWhitespace();
            reader.expect('=');
            result.add(Either.left(reader.getString().substring(reader.getCursor()-1, reader.getCursor())));
            reader.skipWhitespace();
            sectionStartCursor = reader.getCursor();
            if(argumentName.equals("type") && reader.canRead(2) && (reader.peek() == '[' || (reader.peek() == '!' && reader.peek(1) == '['))) {
                if(reader.peek() == '!') reader.skip();
                result.add(Either.left(reader.getString().substring(sectionStartCursor, reader.getCursor()) + '#'));
                reader.skipWhitespace();
                var entryList = VanillaLanguage.Companion.parseRawRegistryTagTuple(reader, BuiltInRegistries.ENTITY_TYPE);
                result.add(Either.right(entryList.getResource()));
                sectionStartCursor = reader.getCursor();
            } else if(argumentName.equals("nbt") && reader.canRead()) {
                if(reader.peek() == '!') reader.skip();
                var previousSection = reader.getString().substring(sectionStartCursor, reader.getCursor());
                reader.skipWhitespace();
                var compound = TagParser.parseCompoundAsArgument(reader);
                result.add(Either.left(previousSection + compound.toString()));
                sectionStartCursor = reader.getCursor();
            } else {
                selectorHandler.handle(selectorReader);
            }
            result.add(Either.left(reader.getString().substring(sectionStartCursor, reader.getCursor())));
            reader.skipWhitespace();
            sectionStartCursor = reader.getCursor();
            if (!reader.canRead() || reader.peek() != ',') {
                break;
            }
            reader.skip();
            sectionStartCursor = reader.getCursor();
        }
        if(reader.canRead()) {
            reader.skip();
        }
        result.add(Either.left(reader.getString().substring(sectionStartCursor, reader.getCursor())));
        return result;
    }
}
