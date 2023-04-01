package net.papierkorb2292.command_crafter.mixin.parser;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import net.minecraft.registry.DynamicRegistryManager;
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
import java.util.Queue;

@Mixin(DataPackContents.class)
public class DataPackContentsMixin implements ParsedResourceCreator.DataPackRefresher {

    @Shadow @Final private FunctionLoader functionLoader;

    private final Queue<Function0<Unit>> command_crafter$refresh_callbacks = new LinkedList<>();

    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    private void command_crafter$addResourceCreatorContextToFunctionLoader(DynamicRegistryManager.Immutable dynamicRegistryManager, FeatureSet enabledFeatures, CommandManager.RegistrationEnvironment environment, int functionPermissionLevel, CallbackInfo ci){
        ((ParsedResourceCreator.ParseResourceContextContainer)functionLoader).command_crafter$setResourceCreatorContext((DataPackContents)(Object)this);
    }

    @Inject(
            method = "refresh",
            at = @At("TAIL")
    )
    private void command_crafter$callRefreshCallbacks(DynamicRegistryManager dynamicRegistryManager, CallbackInfo ci) {
        for(var callback : command_crafter$refresh_callbacks) {
            callback.invoke();
        }
    }

    @Override
    public void command_crafter$addCallback(@NotNull Function0<Unit> callback) {
        command_crafter$refresh_callbacks.add(callback);
    }
}
