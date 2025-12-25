package net.papierkorb2292.command_crafter.mixin.client.editor;

import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ClientLanguage.class)
public interface ClientLanguageAccessor {
    @Accessor
    Map<String, String> getStorage();
}
