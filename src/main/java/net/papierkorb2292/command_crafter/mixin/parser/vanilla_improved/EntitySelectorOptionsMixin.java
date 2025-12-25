package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.core.registries.BuiltInRegistries;
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
                    target = "Lnet/minecraft/commands/arguments/selector/EntitySelectorParser;isTag()Z",
                    remap = true
            ),
            cancellable = true,
            remap = false
    )
    private static void command_crafter$allowInlineEntityTag(EntitySelectorParser reader, CallbackInfo ci, @Local boolean bl) {
        if(VanillaLanguage.Companion.isReaderInlineResources(reader.getReader()) && reader.getReader().canRead() && reader.getReader().peek() == '[') {
            var tag = VanillaLanguage.Companion.parseRegistryTagTuple((DirectiveStringReader<?>) reader.getReader(), BuiltInRegistries.ENTITY_TYPE);
            reader.addPredicate(entity -> tag.contains(entity.getType().builtInRegistryHolder()) != bl);
            ci.cancel();
        }
    }
}
