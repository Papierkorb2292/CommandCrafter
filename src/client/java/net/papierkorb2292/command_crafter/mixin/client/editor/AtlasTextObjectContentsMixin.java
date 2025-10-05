package net.papierkorb2292.command_crafter.mixin.client.editor;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.text.object.AtlasTextObjectContents;
import net.minecraft.util.Identifier;
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.stream.Stream;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(AtlasTextObjectContents.class)
public class AtlasTextObjectContentsMixin {
    @ModifyExpressionValue(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/codecs/RecordCodecBuilder;mapCodec(Ljava/util/function/Function;)Lcom/mojang/serialization/MapCodec;"
            ),
            remap = false
    )
    private static <O> MapCodec<O> command_crafter$addAtlasObjectSuggestions(MapCodec<O> original) {
        return MapCodec.of(original, new MapDecoder<>() {
            @Override
            public <T> DataResult<O> decode(DynamicOps<T> ops, MapLike<T> input) {
                final var analyzingOps = getOrNull(StringRangeTree.AnalyzingDynamicOps.Companion.getCURRENT_ANALYZING_OPS());
                if(analyzingOps != null) {
                    @SuppressWarnings("unchecked")
                    final var castedOps = (StringRangeTree.AnalyzingDynamicOps<T>)analyzingOps;
                    final var atlasInput = input.get("atlas");
                    final var spriteInput = input.get("sprite");
                    if(atlasInput != null) {
                        castedOps.getNodeStartSuggestions(atlasInput).add(() -> {
                            final var atlasSuggestions = new ArrayList<StringRangeTree.Suggestion<T>>();
                            MinecraftClient.getInstance().getAtlasManager().acceptAtlasTextures((id, texture) ->
                                atlasSuggestions.add(new StringRangeTree.Suggestion<>(ops.createString(id.toString()), false))
                            );
                            return atlasSuggestions.stream();
                        });
                    }
                    if(spriteInput != null) {
                        castedOps.getNodeStartSuggestions(spriteInput).add(() -> {
                            Stream<SpriteAtlasTexture> atlasCandidates = null;
                            if(atlasInput != null) {
                                final var atlasIdString = ops.getStringValue(atlasInput).resultOrPartial();
                                if(atlasIdString.isPresent()) {
                                    final var atlasId = Identifier.tryParse(atlasIdString.get());
                                    if(atlasId != null) {
                                        try {
                                            atlasCandidates = Stream.of(MinecraftClient.getInstance().getAtlasManager().getAtlasTexture(atlasId));
                                        } catch (IllegalArgumentException ignored) { /* Let the `atlasCandidates == null` case run instead */ }
                                    }
                                }
                            }
                            if(atlasCandidates == null) {
                                final var atlasTextureList = new ArrayList<SpriteAtlasTexture>();
                                MinecraftClient.getInstance().getAtlasManager().acceptAtlasTextures((id, texture) ->
                                        atlasTextureList.add(texture)
                                );
                                atlasCandidates = atlasTextureList.stream();
                            }
                            return atlasCandidates.flatMap(texture -> ((SpriteAtlasTextureAccessor)texture).getSprites().keySet().stream())
                                    .map(id -> new StringRangeTree.Suggestion<>(ops.createString(id.toString()), false));
                        });
                    }
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
