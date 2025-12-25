package net.papierkorb2292.command_crafter.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import kotlin.Pair;
import net.minecraft.core.Registry;
import net.minecraft.tags.TagLoader;
import net.papierkorb2292.command_crafter.parser.ParsedResourceCreator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.*;

@Mixin(TagLoader.class)
public class TagLoaderMixin {

    private static final ThreadLocal<Map<Registry.PendingTags<?>, Pair<Registry<?>, TagLoader.LoadResult<?>>>> command_crafter$tagData = ThreadLocal.withInitial(HashMap::new);

    @ModifyReturnValue(
            method = "loadPendingTags(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/core/Registry;)Ljava/util/Optional;",
            at = @At("RETURN")
    )
    private static Optional<Registry.PendingTags<?>> command_crafter$populateTagData(@SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<Registry.PendingTags<?>> pendingTagLoad, @Local(argsOnly = true) Registry<?> registry, @Local TagLoader.LoadResult<?> registryTags) {
        pendingTagLoad.ifPresent(tagLoad ->
                command_crafter$tagData.get().put(tagLoad, new Pair<>(registry, registryTags))
        );
        return pendingTagLoad;
    }

    @ModifyReturnValue(
            method = "loadTagsForExistingRegistries(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/core/RegistryAccess;)Ljava/util/List;",
            at = @At("RETURN")
    )
    private static List<Registry.PendingTags<?>> command_crafter$saveTagData(List<Registry.PendingTags<?>> original) {
        final var tagDataMap = command_crafter$tagData.get();
        final var tagDataList = new ArrayList<Pair<Registry<?>, TagLoader.LoadResult<?>>>();
        for (var tagLoad : original) {
            tagDataList.add(tagDataMap.get(tagLoad));
        }
        ParsedResourceCreator.Companion.getPENDING_TAG_LOADS().put(original, tagDataList);
        return original;
    }

}
