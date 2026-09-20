package net.papierkorb2292.command_crafter.mixin.editor;

import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.tags.TagKey;
import net.papierkorb2292.command_crafter.editor.MutableRegistry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(MappedRegistry.class)
public class MappedRegistryMixin<T> implements MutableRegistry {
    @Shadow
    @Final
    private Map<TagKey<T>, HolderSet.Named<T>> frozenTags;

    @Override
    public void command_crafter$removeUnboundTags() {
        var unbound = frozenTags.entrySet().stream()
                .filter(entry -> !entry.getValue().isBound())
                .map(Map.Entry::getKey)
                .toList();
        unbound.forEach(frozenTags::remove);
    }
}
