package net.papierkorb2292.command_crafter.client.editor.processing.codecmod

import com.mojang.serialization.Codec
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.PrimitiveCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.locale.Language
import net.minecraft.network.chat.contents.KeybindContents
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.network.chat.contents.objects.AtlasSprite
import net.minecraft.resources.Identifier
import net.papierkorb2292.command_crafter.codecmod.CodecMod
import net.papierkorb2292.command_crafter.editor.processing.CodecSuggestionWrapper
import net.papierkorb2292.command_crafter.editor.processing.CodecSuggestionWrapper.SuggestionsProvider
import net.papierkorb2292.command_crafter.editor.processing.PrimitiveCodecSuggestionWrapper
import net.papierkorb2292.command_crafter.editor.processing.codecmod.forGetterIdent
import net.papierkorb2292.command_crafter.editor.processing.codecmod.onlyAnalyzingRecord
import net.papierkorb2292.command_crafter.mixin.client.editor.ClientLanguageAccessor
import net.papierkorb2292.command_crafter.mixin.client.editor.KeyMappingAccessor
import net.papierkorb2292.command_crafter.mixin.client.editor.TextureAtlasAccessor
import java.util.stream.Stream

@Suppress("unused")
object ClientCodecTransformers {
    // Only applied clientside, because servers don't know which keybinds there are
    @JvmStatic
    @CodecMod(target = KeybindContents::class, codecField = "keybind")
    fun suggestKeybindContents(codec: PrimitiveCodec<String>): PrimitiveCodec<String> =
        PrimitiveCodecSuggestionWrapper(codec, object : SuggestionsProvider {
            override fun <T : Any> getSuggestions(ops: DynamicOps<T>): Stream<T> {
                return KeyMappingAccessor.getALL().keys.stream().map<T>(ops::createString)
            }
        })

    // Only applied clientside, because servers load languages differently
    @JvmStatic
    @CodecMod(target = TranslatableContents::class, codecField = "translate")
    fun suggestTranslationNames(codec: PrimitiveCodec<String>): PrimitiveCodec<String> =
        PrimitiveCodecSuggestionWrapper(codec, object : SuggestionsProvider {
            override fun <T : Any> getSuggestions(ops: DynamicOps<T>): Stream<T> =
                (Language.getInstance() as? ClientLanguageAccessor)
                    ?.storage?.keys?.stream()
                    ?.map<T>(ops::createString)
                    ?: Stream.empty()
        })

    @JvmStatic
    @CodecMod(target = AtlasSprite::class, codecField = "sprite", includeCodecField = true)
    fun suggestAtlasSpriteNames(codec: MapCodec<Identifier>): MapCodec<Identifier> {
        return RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                CodecSuggestionWrapper.simple(Identifier.CODEC, object : SuggestionsProvider {
                    override fun <T : Any> getSuggestions(ops: DynamicOps<T>): Stream<T> {
                        val atlasSuggestions = ArrayList<T>()
                        Minecraft.getInstance().atlasManager.forEach { id, _ ->
                            atlasSuggestions.add(ops.createString(id.toString()))
                        }
                        return atlasSuggestions.stream()
                    }
                }).onlyAnalyzingRecord("atlas"),
                Codec.PASSTHROUGH.onlyAnalyzingRecord("sprite"),
                codec.forGetterIdent(),
            ).apply(instance) { atlas, sprite, result ->
                if(sprite.isEmpty) return@apply result
                CodecSuggestionWrapper.simple(Codec.STRING, object : SuggestionsProvider {
                    override fun <T : Any> getSuggestions(ops: DynamicOps<T>): Stream<T> {
                        var atlasCandidates: Stream<TextureAtlas>? = null
                        atlas.ifPresent { atlasId ->
                            try {
                                atlasCandidates = Stream.of(Minecraft.getInstance().atlasManager.getAtlasOrThrow(atlasId))
                            } catch(ignored: IllegalArgumentException) { /* Let the `atlasCandidates == null` case run instead */ }
                        }
                        if(atlasCandidates == null) {
                            val atlasTextureList = mutableListOf<TextureAtlas>()
                            Minecraft.getInstance().atlasManager.forEach { _, texture ->
                                atlasTextureList.add(texture)
                            }
                            atlasCandidates = atlasTextureList.stream()
                        }
                        return atlasCandidates
                            .flatMap { texture -> (texture as TextureAtlasAccessor).getTexturesByName().keys.stream() }
                            .map { id -> ops.createString(id.toString()) }
                    }
                }).decode(sprite.get())
                result
            }
        }
    }
}