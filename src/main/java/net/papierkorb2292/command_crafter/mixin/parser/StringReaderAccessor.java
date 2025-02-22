package net.papierkorb2292.command_crafter.mixin.parser;

import com.mojang.brigadier.StringReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StringReader.class)
public interface StringReaderAccessor {

    // Named with _ to not cause recursion when called from DirectStringReader.setString
    @Mutable @Accessor(value = "string", remap = false)
    void _setString(String string);
}
