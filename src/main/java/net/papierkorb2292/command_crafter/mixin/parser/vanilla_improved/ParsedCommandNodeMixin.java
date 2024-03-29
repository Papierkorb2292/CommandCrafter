package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.mojang.brigadier.context.ParsedCommandNode;
import net.papierkorb2292.command_crafter.parser.helper.CursorOffsetContainer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ParsedCommandNode.class)
public class ParsedCommandNodeMixin implements CursorOffsetContainer {

    private int command_crafter$readCharacters;
    private int command_crafter$skippedChars;

    @Override
    public void command_crafter$setCursorOffset(int readCharacters, int skippedChars) {
        command_crafter$readCharacters = readCharacters;
        command_crafter$skippedChars = skippedChars;
    }

    @Override
    public int command_crafter$getReadCharacters() {
        return command_crafter$readCharacters;
    }

    @Override
    public int command_crafter$getSkippedChars() {
        return command_crafter$skippedChars;
    }
}
