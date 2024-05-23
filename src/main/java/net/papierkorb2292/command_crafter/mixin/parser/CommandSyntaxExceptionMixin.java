package net.papierkorb2292.command_crafter.mixin.parser;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(CommandSyntaxException.class)
public class CommandSyntaxExceptionMixin implements VanillaLanguage.CursorAwareException {

    @Shadow(remap = false) @Final private int cursor;

    @Override
    public int command_crafter$getCursor() {
        return cursor;
    }
}
