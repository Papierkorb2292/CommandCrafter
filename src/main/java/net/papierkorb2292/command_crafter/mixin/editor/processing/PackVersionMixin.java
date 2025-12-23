package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.serialization.*;
import kotlin.Unit;
import net.minecraft.SharedConstants;
import net.minecraft.resource.PackVersion;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.dynamic.Range;
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.stream.Stream;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(PackVersion.class)
public class PackVersionMixin {

    @ModifyReturnValue(
            method = "createRangeCodec",
            at = @At("RETURN")
    )
    private static MapCodec<Range<PackVersion>> command_crafter$suggestCurrentVersion(MapCodec<Range<PackVersion>> original, ResourceType type) {
        final var currentVersion = SharedConstants.getGameVersion().packVersion(type);

        return MapCodec.of(original, new MapDecoder<>() {
            @Override
            public <T> DataResult<Range<PackVersion>> decode(DynamicOps<T> ops, MapLike<T> input) {
                final var analyzingOps = getOrNull(StringRangeTree.AnalyzingDynamicOps.Companion.getCURRENT_ANALYZING_OPS());
                if(analyzingOps != null) {
                    @SuppressWarnings("unchecked")
                    final var castedOps = (StringRangeTree.AnalyzingDynamicOps<T>)analyzingOps;
                    final var minFormatInput = input.get("min_format");
                    final var maxFormatInput = input.get("max_format");

                    final StringRangeTree.SuggestionProvider<T> suggestionProvider = () ->
                            PackVersion.CODEC.encode(currentVersion, ops, ops.empty()).result().stream()
                                    .map(encoded -> new StringRangeTree.Suggestion<>(encoded, false, completion -> {
                                        completion.setDetail("Minecraft's current pack version");
                                        return Unit.INSTANCE;
                                    }));


                    if(minFormatInput != null)
                        castedOps.getNodeStartSuggestions(minFormatInput).add(suggestionProvider);
                    if(maxFormatInput != null)
                        castedOps.getNodeStartSuggestions(maxFormatInput).add(suggestionProvider);
                }
                return original.decode(ops, input);
            }

            @Override
            public <T> KeyCompressor<T> compressor(DynamicOps<T> ops) {
                return original.compressor(ops);
            }

            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return original.keys(ops);
            }
        });
    }
}
