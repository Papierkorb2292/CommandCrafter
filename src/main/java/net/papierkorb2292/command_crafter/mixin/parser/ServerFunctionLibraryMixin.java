package net.papierkorb2292.command_crafter.mixin.parser;

import com.google.common.collect.ImmutableMap;
import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.context.StringRange;
import com.mojang.datafixers.util.Pair;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.commands.functions.CommandParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.ServerFunctionLibrary;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.TagLoader;
import net.papierkorb2292.command_crafter.editor.PackagedId;
import net.papierkorb2292.command_crafter.editor.debugger.helper.FinalTagContentProvider;
import net.papierkorb2292.command_crafter.editor.debugger.helper.UtilKt;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugHandler;
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.FileMappingInfo;
import net.papierkorb2292.command_crafter.parser.LanguageManager;
import net.papierkorb2292.command_crafter.parser.ParsedResourceCreator;
import net.papierkorb2292.command_crafter.parser.helper.FileSourceContainer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@SuppressWarnings("unused")
@Mixin(ServerFunctionLibrary.class)
public class ServerFunctionLibraryMixin implements ParsedResourceCreator.ParseResourceContextContainer {

    @Shadow @Final private TagLoader<CommandFunction<CommandSourceStack>> tagsLoader;
    @Shadow @Final private CommandParser.Provider<CommandSourceStack> parserProvider;
    private @Nullable ReloadableServerResources command_crafter$resourceCreatorContext;

    @WrapOperation(
            method = "reload ->* get()Lnet/minecraft/commands/functions/CommandFunction;", // Targets lambda passed to supplyAsync for each function
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/functions/CommandFunction;fromLines(Lnet/minecraft/resources/Identifier;Lnet/minecraft/commands/functions/CommandParser$Provider;Ljava/util/List;)Lnet/minecraft/commands/functions/CommandFunction;"
            )
    )
    private <TRuntime extends ExecutionCommandSource<TRuntime>> CommandFunction<TRuntime> command_crafter$replaceFunctionCreationWithDirectiveParser(Identifier id, CommandParser.Provider<TRuntime> parserProvider, List<String> lines, Operation<CommandFunction<TRuntime>> original, @Local(argsOnly = true) Map.Entry<Identifier, Resource> resourceEntry) {
        @SuppressWarnings("unchecked")
        var parserProviderCast = (CommandParser.Provider<CommandSourceStack>) parserProvider;
        var resourceCreator = new ParsedResourceCreator(id, resourceEntry.getValue().sourcePackId(), parserProviderCast);
        var infoSetCallbacks = new ArrayList<Function1<? super ParsedResourceCreator.ResourceStackInfo, Unit>>();
        resourceCreator.getOriginResourceIdSetEventStack().push((idSetter) -> idSetter.invoke(id));
        resourceCreator.getOriginResourceInfoSetEventStack().push((infoSetter) -> {
            infoSetCallbacks.add(infoSetter);
            return Unit.INSTANCE;
        });
        var reader = new DirectiveStringReader<>(FileMappingInfo.Companion.fromLines(lines), resourceCreator);
        var startCursor = reader.getAbsoluteCursor();
        var functionBuilder = LanguageManager.INSTANCE.parseToCommands(
                reader,
                LanguageManager.INSTANCE.getDEFAULT_CLOSURE()
        );

        var functionStackInfo = new ParsedResourceCreator.ResourceStackInfo(id, new StringRange(startCursor, reader.getAbsoluteCursor()));
        for(var callback : infoSetCallbacks) {
            callback.invoke(functionStackInfo);
        }
        var function = functionBuilder.build(id, parserProviderCast);
        if(function instanceof FileSourceContainer container) {
            container.command_crafter$setFileSource(lines, UtilKt.withExtension(id, FunctionDebugHandler.Companion.getFUNCTION_FILE_EXTENSTION()));
        }
        ParsedResourceCreator.Companion.addResourceCreatorToFunction(function, resourceCreator);
        resourceCreator.getOriginResourceIdSetEventStack().pop();
        ((FinalTagContentProvider) tagsLoader).command_crafter$getFileContent().put(
                new PackagedId(
                        Identifier.fromNamespaceAndPath(id.getNamespace(), PackContentFileType.Companion.getFUNCTIONS_FILE_TYPE().getContentTypePath() + "/" + id.getPath() + FunctionDebugHandler.Companion.getFUNCTION_FILE_EXTENSTION()),
                        PackagedId.Companion.getPackIdWithoutPrefix(resourceEntry.getValue().sourcePackId())
                ),
                reader.getFileMappingInfo()
        );
        //noinspection unchecked
        return (CommandFunction<TRuntime>) function;
    }

    @ModifyReceiver(
            method = "reload -> Ljava/util/function/Consumer;accept(Lcom/mojang/datafixers/util/Pair;)V",  // Targets lambda passed to final thenAcceptAsync
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/ImmutableMap$Builder;build()Lcom/google/common/collect/ImmutableMap;"
            )
    )
    private ImmutableMap.Builder<Identifier, CommandFunction<CommandSourceStack>> command_crafter$addResourcesCreatedByFunctions(ImmutableMap.Builder<Identifier, CommandFunction<CommandSourceStack>> builder, Pair<Map<Identifier, List<TagLoader.EntryWithSource>>, HashMap<Identifier, CompletableFuture<CommandFunction<CommandSourceStack>>>> intermediate) throws ExecutionException, InterruptedException {
        for(var function : intermediate.getSecond().values()) {
            if(function.isCompletedExceptionally()) continue;
            ParsedResourceCreator.Companion.createResources(function.get(), builder, intermediate.getFirst());
        }
        return builder;
    }

    @Override
    public ReloadableServerResources command_crafter$getResourceCreatorContext() {
        return command_crafter$resourceCreatorContext;
    }

    @Override
    public void command_crafter$setResourceCreatorContext(ReloadableServerResources resourceCreatorContext) {
        this.command_crafter$resourceCreatorContext = resourceCreatorContext;
    }
}
