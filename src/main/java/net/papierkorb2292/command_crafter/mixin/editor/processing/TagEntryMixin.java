package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import kotlin.Unit;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.TagEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTreeJsonResourceAnalyzer;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(TagEntry.class)
public class TagEntryMixin {

    @ModifyExpressionValue(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE:LAST",
                    target = "Lcom/mojang/serialization/Codec;xmap(Ljava/util/function/Function;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;",
                    remap = false
            ),
            slice = @Slice(
                    to = @At(
                            value = "FIELD",
                            target = "Lnet/minecraft/registry/tag/TagEntry;CODEC:Lcom/mojang/serialization/Codec;",
                            opcode = Opcodes.PUTSTATIC
                    )
            )
    )
    private static Codec<TagEntry> command_crafter$checkIdsWhenAnalyzingDecoder(Codec<TagEntry> original) {
        return original.flatXmap(tagEntry -> {
            final var registry = getOrNull(StringRangeTreeJsonResourceAnalyzer.Companion.getCURRENT_TAG_ANALYZING_REGISTRY());
            if (registry == null)
                return DataResult.success(tagEntry);
            final var entryExists = tagEntry.resolve(new TagEntry.ValueGetter<>() {
                @Override
                public @Nullable Object direct(Identifier id, boolean required) {
                    //noinspection rawtypes,unchecked
                    return registry.getOptional(RegistryKey.of(((RegistryKey)registry.getKey()), id)).orElse(null);
                }

                @Override
                public @Nullable Collection<Object> tag(Identifier id) {
                    //noinspection rawtypes,unchecked
                    final Optional<Object> tag = registry.getOptional(TagKey.of(((RegistryKey)registry.getKey()), id));
                    // Return null when no tag was found and not null when a tag was found
                    return tag.map(ignored -> Collections.emptyList()).orElse(null);
                }
            }, id -> {});
            if(!entryExists)
                return DataResult.error(() -> "Could not find tag entry: " + tagEntry);
            return DataResult.success(tagEntry);
        }, DataResult::success);
    }
}
