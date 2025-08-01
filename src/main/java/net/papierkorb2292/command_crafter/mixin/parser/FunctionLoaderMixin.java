package net.papierkorb2292.command_crafter.mixin.parser;

import com.google.common.collect.ImmutableMap;
import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.StringRange;
import com.mojang.datafixers.util.Pair;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import net.minecraft.command.CommandSource;
import net.minecraft.registry.tag.TagGroupLoader;
import net.minecraft.resource.Resource;
import net.minecraft.server.DataPackContents;
import net.minecraft.server.command.AbstractServerCommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.function.CommandFunction;
import net.minecraft.server.function.FunctionLoader;
import net.minecraft.util.Identifier;
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
import net.papierkorb2292.command_crafter.parser.helper.SplitProcessedInputCursorMapper;
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
@Mixin(FunctionLoader.class)
public class FunctionLoaderMixin implements ParsedResourceCreator.ParseResourceContextContainer {

    @Shadow @Final private TagGroupLoader<CommandFunction<ServerCommandSource>> tagLoader;
    private @Nullable DataPackContents command_crafter$resourceCreatorContext;

    @SuppressWarnings("DefaultAnnotationParam")
    @WrapOperation(
            method = "method_29451",
            remap = false,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/function/CommandFunction;create(Lnet/minecraft/util/Identifier;Lcom/mojang/brigadier/CommandDispatcher;Lnet/minecraft/server/command/AbstractServerCommandSource;Ljava/util/List;)Lnet/minecraft/server/function/CommandFunction;",
                    remap = true
            )
    )
    private <T extends AbstractServerCommandSource<T>> CommandFunction<T> command_crafter$replaceFunctionCreationWithDirectiveParser(Identifier id, CommandDispatcher<T> dispatcher, T source, List<String> lines, Operation<CommandFunction<T>> op, Map.Entry<Identifier, Resource> resourceEntry) {
        if(!(source instanceof ServerCommandSource serverSource)) {
            //noinspection MixinExtrasOperationParameters
            return op.call(id, dispatcher, source, lines);
        }
        var resourceCreator = command_crafter$resourceCreatorContext == null ? null : new ParsedResourceCreator(id, resourceEntry.getValue().getPackId(), command_crafter$resourceCreatorContext);
        var infoSetCallbacks = new ArrayList<Function1<? super ParsedResourceCreator.ResourceStackInfo, Unit>>();
        if(resourceCreator != null) {
            resourceCreator.getOriginResourceIdSetEventStack().push((idSetter) -> idSetter.invoke(id));
            resourceCreator.getOriginResourceInfoSetEventStack().push((infoSetter) -> {
                infoSetCallbacks.add(infoSetter);
                return Unit.INSTANCE;
            });
        }
        @SuppressWarnings("unchecked")
        var reader = new DirectiveStringReader<>(new FileMappingInfo(lines, new SplitProcessedInputCursorMapper(), 0, 0), (CommandDispatcher<CommandSource>)(Object)dispatcher, resourceCreator);
        var startCursor = reader.getAbsoluteCursor();
        var functionBuilder = LanguageManager.INSTANCE.parseToCommands(
                reader,
                serverSource,
                LanguageManager.INSTANCE.getDEFAULT_CLOSURE()
        );

        var functionStackInfo = new ParsedResourceCreator.ResourceStackInfo(id, new StringRange(startCursor, reader.getAbsoluteCursor()));
        for(var callback : infoSetCallbacks) {
            callback.invoke(functionStackInfo);
        }
        var function = functionBuilder.toCommandFunction(id);
        if(function instanceof FileSourceContainer container) {
            container.command_crafter$setFileSource(lines, UtilKt.withExtension(id, FunctionDebugHandler.Companion.getFUNCTION_FILE_EXTENSTION()));
        }
        ParsedResourceCreator.Companion.addResourceCreatorToFunction(function, resourceCreator);
        if(resourceCreator != null) {
            resourceCreator.getOriginResourceIdSetEventStack().pop();
        }
        ((FinalTagContentProvider)tagLoader).command_crafter$getFileContent().put(
                new PackagedId(
                        Identifier.of(id.getNamespace(), PackContentFileType.FUNCTIONS_FILE_TYPE.getContentTypePath() + "/" + id.getPath() + FunctionDebugHandler.Companion.getFUNCTION_FILE_EXTENSTION()),
                        PackagedId.Companion.getPackIdWithoutPrefix(resourceEntry.getValue().getPackId())
                ),
                reader.getFileMappingInfo()
        );
        //noinspection unchecked
        return (CommandFunction<T>) function;
    }

    @ModifyReceiver(
            method = "method_29453",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/ImmutableMap$Builder;build()Lcom/google/common/collect/ImmutableMap;"
            ),
            remap = false
    )
    private ImmutableMap.Builder<Identifier, CommandFunction<ServerCommandSource>> command_crafter$addResourcesCreatedByFunctions(ImmutableMap.Builder<Identifier, CommandFunction<ServerCommandSource>> builder, Pair<Map<Identifier, List<TagGroupLoader.TrackedEntry>>, HashMap<Identifier, CompletableFuture<CommandFunction<ServerCommandSource>>>> intermediate) throws ExecutionException, InterruptedException {
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
