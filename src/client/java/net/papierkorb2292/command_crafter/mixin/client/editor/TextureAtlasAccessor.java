package net.papierkorb2292.command_crafter.mixin.client.editor;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(TextureAtlas.class)
public interface TextureAtlasAccessor {
    @Accessor
    Map<Identifier, TextureAtlasSprite> getTexturesByName();
}
