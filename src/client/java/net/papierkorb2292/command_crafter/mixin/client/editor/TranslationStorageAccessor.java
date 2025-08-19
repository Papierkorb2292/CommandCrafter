package net.papierkorb2292.command_crafter.mixin.client.editor;

import net.minecraft.client.resource.language.TranslationStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(TranslationStorage.class)
public interface TranslationStorageAccessor {
    @Accessor
    Map<String, String> getTranslations();
}
