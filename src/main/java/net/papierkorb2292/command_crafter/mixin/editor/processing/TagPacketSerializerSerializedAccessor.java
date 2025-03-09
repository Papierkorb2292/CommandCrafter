package net.papierkorb2292.command_crafter.mixin.editor.processing;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.registry.tag.TagPacketSerializer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(TagPacketSerializer.Serialized.class)
public interface TagPacketSerializerSerializedAccessor {
    @Invoker("<init>")
    static TagPacketSerializer.Serialized callInit(Map<Identifier, IntList> contents) {
        throw new AssertionError();
    }
}
