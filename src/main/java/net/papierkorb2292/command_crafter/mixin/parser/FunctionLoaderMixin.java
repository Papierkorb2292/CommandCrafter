package net.papierkorb2292.command_crafter.mixin.parser;

import com.google.common.collect.ImmutableMap;
import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.datafixers.util.Pair;
import net.minecraft.registry.tag.TagGroupLoader;
import net.minecraft.server.DataPackContents;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.function.CommandFunction;
import net.minecraft.server.function.FunctionLoader;
import net.minecraft.util.Identifier;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.Language;
import net.papierkorb2292.command_crafter.parser.LanguageManager;
import net.papierkorb2292.command_crafter.parser.ParsedResourceCreator;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@SuppressWarnings("unused")
@Mixin(FunctionLoader.class)
public class FunctionLoaderMixin implements ParsedResourceCreator.ParseResourceContextContainer {

    private @Nullable DataPackContents command_crafter$resourceCreatorContext;

    @SuppressWarnings("DefaultAnnotationParam")
    @Redirect(
            method = "method_29451",
            remap = false,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/function/CommandFunction;create(Lnet/minecraft/util/Identifier;Lcom/mojang/brigadier/CommandDispatcher;Lnet/minecraft/server/command/ServerCommandSource;Ljava/util/List;)Lnet/minecraft/server/function/CommandFunction;",
                    remap = true
            )
    )
    private CommandFunction command_crafter$replaceFunctionCreationWithDirectiveParser(Identifier id, CommandDispatcher<ServerCommandSource> dispatcher, ServerCommandSource source, List<String> lines) {
        var resourceCreator = command_crafter$resourceCreatorContext == null ? null : new ParsedResourceCreator(id, command_crafter$resourceCreatorContext);
        if(resourceCreator != null) {
            resourceCreator.getOriginResourceIdSetEventStack().push((idSetter) -> idSetter.invoke(id));
        }
        var function = ParsedResourceCreator.Companion.createResourceCreatorFunction(
                id,
                LanguageManager.INSTANCE.parseToCommands(
                        new DirectiveStringReader<>(lines, dispatcher, resourceCreator),
                        source,
                        new Language.TopLevelClosure(VanillaLanguage.NORMAL)),
                resourceCreator);
        if(resourceCreator != null) {
            resourceCreator.getOriginResourceIdSetEventStack().pop();
        }
        return function;
    }

    @ModifyReceiver(
            method = "method_29453",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/ImmutableMap$Builder;build()Lcom/google/common/collect/ImmutableMap;"
            ),
            remap = false
    )
    private ImmutableMap.Builder<Identifier, CommandFunction> command_crafter$addResourcesCreatedByFunctions(ImmutableMap.Builder<Identifier, CommandFunction> builder, Pair<Map<Identifier, List<TagGroupLoader.TrackedEntry>>, HashMap<Identifier, CompletableFuture<CommandFunction>>> intermediate) throws ExecutionException, InterruptedException {
        for(var function : intermediate.getSecond().values()) {
            if(function.isCompletedExceptionally()) continue;
            ParsedResourceCreator.Companion.createResources(function.get(), builder, intermediate.getFirst());
        }
        return builder;
    }

    @Override
    public DataPackContents command_crafter$getResourceCreatorContext() {
        return command_crafter$resourceCreatorContext;
    }

    @Override
    public void command_crafter$setResourceCreatorContext(DataPackContents resourceCreatorContext) {
        this.command_crafter$resourceCreatorContext = resourceCreatorContext;
    }
}
