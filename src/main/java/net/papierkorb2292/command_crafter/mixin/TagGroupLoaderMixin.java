package net.papierkorb2292.command_crafter.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import kotlin.Pair;
import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.TagGroupLoader;
import net.papierkorb2292.command_crafter.parser.ParsedResourceCreator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.*;

@Mixin(TagGroupLoader.class)
public class TagGroupLoaderMixin {

    private static final ThreadLocal<Map<Registry.PendingTagLoad<?>, Pair<Registry<?>, TagGroupLoader.RegistryTags<?>>>> command_crafter$tagData = ThreadLocal.withInitial(HashMap::new);

    @ModifyReturnValue(
            method = "startReload(Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/registry/Registry;)Ljava/util/Optional;",
            at = @At("RETURN")
    )
    private static Optional<Registry.PendingTagLoad<?>> command_crafter$populateTagData(@SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<Registry.PendingTagLoad<?>> pendingTagLoad, @Local(argsOnly = true) Registry<?> registry, @Local TagGroupLoader.RegistryTags<?> registryTags) {
        pendingTagLoad.ifPresent(tagLoad ->
                command_crafter$tagData.get().put(tagLoad, new Pair<>(registry, registryTags))
        );
        return pendingTagLoad;
    }

    @ModifyReturnValue(
            method = "startReload(Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/registry/DynamicRegistryManager;)Ljava/util/List;",
            at = @At("RETURN")
    )
    private static List<Registry.PendingTagLoad<?>> command_crafter$saveTagData(List<Registry.PendingTagLoad<?>> original) {
        final var tagDataMap = command_crafter$tagData.get();
        final var tagDataList = new ArrayList<Pair<Registry<?>, TagGroupLoader.RegistryTags<?>>>();
        for (var tagLoad : original) {
            tagDataList.add(tagDataMap.get(tagLoad));
        }
        ParsedResourceCreator.Companion.getPENDING_TAG_LOADS().put(original, tagDataList);
        return original;
    }

}
