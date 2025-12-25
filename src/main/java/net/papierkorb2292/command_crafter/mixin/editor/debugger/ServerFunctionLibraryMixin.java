package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.mojang.datafixers.util.Pair;
import net.minecraft.tags.TagLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.server.ServerFunctionLibrary;
import net.minecraft.resources.Identifier;
import net.papierkorb2292.command_crafter.editor.debugger.DebugInformation;
import net.papierkorb2292.command_crafter.editor.debugger.helper.FinalTagContentProvider;
import net.papierkorb2292.command_crafter.editor.debugger.helper.IdentifiedDebugInformationProvider;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags.FunctionTagBreakpointLocation;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags.FunctionTagDebugFrame;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags.RangeFunctionTagDebugInformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(ServerFunctionLibrary.class)
public class ServerFunctionLibraryMixin implements IdentifiedDebugInformationProvider<FunctionTagBreakpointLocation, FunctionTagDebugFrame> {

    @Shadow @Final private TagLoader<CommandFunction<CommandSourceStack>> tagsLoader;
    private Map<Identifier, RangeFunctionTagDebugInformation> command_crafter$tagDebugInformations = new HashMap<>();

    @Inject(
            method = "method_29453",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/tags/TagLoader;build(Ljava/util/Map;)Ljava/util/Map;",
                    shift = At.Shift.AFTER
            )
    )
    private void command_crafter$createTagBreakpointParsers(Pair<?, ?> intermediate, CallbackInfo ci) {
        command_crafter$tagDebugInformations = RangeFunctionTagDebugInformation.Companion.fromFinalTagContentProvider((FinalTagContentProvider) tagsLoader);
    }

    @Nullable
    @Override
    public DebugInformation<FunctionTagBreakpointLocation, FunctionTagDebugFrame> command_crafter$getDebugInformation(@NotNull Identifier id) {
        return command_crafter$tagDebugInformations.get(id);
    }
}
