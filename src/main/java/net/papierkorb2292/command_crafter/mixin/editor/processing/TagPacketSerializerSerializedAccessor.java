package net.papierkorb2292.command_crafter.mixin.editor.processing;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(TagNetworkSerialization.NetworkPayload.class)
public interface TagPacketSerializerSerializedAccessor {
    @Invoker("<init>")
    static TagNetworkSerialization.NetworkPayload callInit(Map<Identifier, IntList> contents) {
        throw new AssertionError();
    }
}
