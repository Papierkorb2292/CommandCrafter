package net.papierkorb2292.command_crafter.mixin.parser;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import net.minecraft.command.permission.PermissionPredicate;
import net.minecraft.registry.CombinedDynamicRegistries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.ServerDynamicRegistryType;
import net.minecraft.registry.tag.TagGroupLoader;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.server.DataPackContents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.function.FunctionLoader;
import net.papierkorb2292.command_crafter.parser.ParsedResourceCreator;
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

@Mixin(DataPackContents.class)
public class DataPackContentsMixin implements ParsedResourceCreator.DataPackRefresher {

    @Shadow @Final private FunctionLoader functionLoader;

    @Shadow @Final private List<Registry.PendingTagLoad<?>> pendingTagLoads;
    private final Queue<Function0<Unit>> command_crafter$refresh_callbacks = new LinkedList<>();

    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    private void command_crafter$addResourceCreatorContextToFunctionLoader(CombinedDynamicRegistries<ServerDynamicRegistryType> dynamicRegistries, RegistryWrapper.WrapperLookup registries, FeatureSet enabledFeatures, CommandManager.RegistrationEnvironment environment, List<Registry.PendingTagLoad<?>> pendingTagLoads, PermissionPredicate permissions, CallbackInfo ci){
        ((ParsedResourceCreator.ParseResourceContextContainer)functionLoader).command_crafter$setResourceCreatorContext((DataPackContents)(Object)this);
    }

    @Inject(
            method = "applyPendingTagLoads",
            at = @At("TAIL")
    )
    private void command_crafter$loadTagsAndCallRefreshCallbacks(CallbackInfo ci) {
        final var newTagData = ParsedResourceCreator.Companion.getPENDING_TAG_LOADS().get(pendingTagLoads);
        for(final var registryTagData : newTagData) {
            //noinspection unchecked
            ((Registry<Object>)registryTagData.getFirst())
                    .startTagReload((TagGroupLoader.RegistryTags<Object>)registryTagData.getSecond())
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
}
