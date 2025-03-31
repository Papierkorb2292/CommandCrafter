package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.command.EntitySelectorOptions;
import net.minecraft.command.EntitySelectorReader;
import net.minecraft.registry.Registries;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntitySelectorOptions.class)
public class EntitySelectorOptionsMixin {

    @SuppressWarnings({"deprecation", "DefaultAnnotationParam"})
    @Inject(
            method = "method_9973",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/EntitySelectorReader;readTagCharacter()Z",
                    remap = true
            ),
            cancellable = true,
            remap = false
    )
    private static void command_crafter$allowInlineEntityTag(EntitySelectorReader reader, CallbackInfo ci, @Local boolean bl) {
        if(VanillaLanguage.Companion.isReaderInlineResources(reader.getReader()) && reader.getReader().canRead() && reader.getReader().peek() == '[') {
            var tag = VanillaLanguage.Companion.parseRegistryTagTuple((DirectiveStringReader<?>) reader.getReader(), Registries.ENTITY_TYPE);
            reader.addPredicate(entity -> tag.contains(entity.getType().getRegistryEntry()) != bl);
            ci.cancel();
        }
    }
}
