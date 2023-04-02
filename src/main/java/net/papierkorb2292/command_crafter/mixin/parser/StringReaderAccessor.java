package net.papierkorb2292.command_crafter.mixin.parser;

import com.mojang.brigadier.StringReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StringReader.class)
public interface StringReaderAccessor {

    @Mutable @Accessor(remap = false)
    void setString(String string);
}
