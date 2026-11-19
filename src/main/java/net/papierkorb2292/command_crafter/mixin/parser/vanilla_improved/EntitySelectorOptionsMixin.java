package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntitySelectorOptions.class)
public class EntitySelectorOptionsMixin {

    @SuppressWarnings({"deprecation", "DefaultAnnotationParam"})
    @Inject(
            method = "bootStrap ->{1} Lnet/minecraft/commands/arguments/selector/options/EntitySelectorOptions$Modifier;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/arguments/selector/EntitySelectorParser;isTag()Z"
            ),
            slice = @Slice(
                    from = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/commands/arguments/selector/EntitySelectorParser;typeOption()Lnet/minecraft/commands/arguments/selector/options/InvertableSetOptionState;"
                    )
            ),
            cancellable = true
    )
    private static void command_crafter$allowInlineEntityTag(EntitySelectorParser parser, CallbackInfo ci, @Local(name = "inverted") boolean inverted) {
        if(VanillaLanguage.Companion.isReaderInlineResources(parser.getReader()) && parser.getReader().canRead() && parser.getReader().peek() == '[') {
            // TODO: Use holder getter provider
            var tag = VanillaLanguage.Companion.parseRegistryTagTuple((DirectiveStringReader<?>) parser.getReader(), BuiltInRegistries.ENTITY_TYPE, Registries.ENTITY_TYPE);
            parser.addPredicate(entity -> tag.contains(entity.getType().builtInRegistryHolder()) != inverted);
            ci.cancel();
        }
    }
}
