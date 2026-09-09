package net.papierkorb2292.command_crafter.mixin.parser;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.ServerFunctionLibrary;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.flag.FeatureFlagSet;
import net.papierkorb2292.command_crafter.parser.ParsedResourceCreator;
import net.papierkorb2292.command_crafter.parser.helper.HolderLookupProviderContainer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

@Mixin(ReloadableServerResources.class)
public class ReloadableServerResourcesMixin implements ParsedResourceCreator.DataPackRefresher, HolderLookupProviderContainer {

    @Shadow @Final private ServerFunctionLibrary functionLibrary;

    @Shadow @Final private List<Registry.PendingTags<?>> postponedTags;
    private final Queue<Function0<Unit>> command_crafter$refresh_callbacks = new LinkedList<>();
    private HolderLookup.Provider command_crafter$holderLookups;

    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    private void command_crafter$addResourceCreatorContextToFunctionLoader(ReloadableServerRegistries.LoadResult loadingContext, FeatureFlagSet enabledFeatures, Commands.CommandSelection commandSelection, List postponedTags, PermissionSet functionCompilationPermissions, List newComponents, CallbackInfo ci){
        ((ParsedResourceCreator.ParseResourceContextContainer) functionLibrary).command_crafter$setResourceCreatorContext((ReloadableServerResources)(Object)this);
        command_crafter$holderLookups = loadingContext.lookupWithUpdatedTags();
    }

    @Inject(
            method = "updateComponentsAndStaticRegistryTags",
            at = @At("TAIL")
    )
    private void command_crafter$loadTagsAndCallRefreshCallbacks(CallbackInfo ci) {
        final var newTagData = ParsedResourceCreator.Companion.getPENDING_TAG_LOADS().get(postponedTags);
        for(final var registryTagData : newTagData) {
            //noinspection unchecked
            ((Registry<Object>)registryTagData.getFirst())
                    .prepareTagReload((TagLoader.LoadResult<Object>)registryTagData.getSecond())
                    .apply();
        }

        for(var callback : command_crafter$refresh_callbacks) {
            callback.invoke();
        }
    }

    @Override
    public void command_crafter$addCallback(@NotNull Function0<Unit> callback) {
        command_crafter$refresh_callbacks.add(callback);
    }

    @Override
    public HolderLookup.@NotNull Provider command_crafter$getHolderLookups() {
        return command_crafter$holderLookups;
    }
}
