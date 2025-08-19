package net.papierkorb2292.command_crafter.mixin.editor.processing;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(targets = "net/minecraft/util/Language$1")
public interface LanguageImplAccessor {
    @Accessor
    Map<String, String> getField_25308();
}
