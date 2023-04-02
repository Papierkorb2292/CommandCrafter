package net.papierkorb2292.command_crafter.mixin.parser;

import net.minecraft.server.function.CommandFunction;
import net.papierkorb2292.command_crafter.parser.ParsedResourceCreator;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CommandFunction.class)
public class CommandFunctionMixin implements ParsedResourceCreator.ParseResourceContainer{

    private ParsedResourceCreator command_crafter$resourceCreator;

    public void command_crafter$setResourceCreator(ParsedResourceCreator command_crafter$resourceCreator) {
        this.command_crafter$resourceCreator = command_crafter$resourceCreator;
    }

    public ParsedResourceCreator command_crafter$getResourceCreator() {
        return command_crafter$resourceCreator;
    }
}
