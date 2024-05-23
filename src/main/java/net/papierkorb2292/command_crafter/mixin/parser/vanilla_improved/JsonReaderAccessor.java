package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.google.gson.stream.JsonReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(JsonReader.class)
public interface JsonReaderAccessor {
    @Accessor(remap = false) int getPos();
    @Accessor(remap = false) int getLimit();
}
