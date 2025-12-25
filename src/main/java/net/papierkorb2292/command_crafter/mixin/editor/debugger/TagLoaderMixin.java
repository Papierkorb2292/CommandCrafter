package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagFile;
import net.minecraft.tags.TagLoader;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.Identifier;
import net.papierkorb2292.command_crafter.editor.PackagedId;
import net.papierkorb2292.command_crafter.editor.debugger.helper.FinalTagContentProvider;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags.FunctionTagDebugHandler;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags.TagFinalEntriesValueGetter;
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTreeJsonReader;
import net.papierkorb2292.command_crafter.parser.FileMappingInfo;
import net.papierkorb2292.command_crafter.parser.helper.SplitProcessedInputCursorMapper;
import net.papierkorb2292.command_crafter.string_range_gson.Strictness;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Mixin(TagLoader.class)
public class TagLoaderMixin<T> implements FinalTagContentProvider {

    @Shadow @Final private String directory;
    private final ThreadLocal<Map<Identifier, List<TagLoader.EntryWithSource>>> command_crafter$parsedTags = new ThreadLocal<>();
    private final Map<PackagedId, FileMappingInfo> command_crafter$tagFileLines = new ConcurrentHashMap<>(64);
    private final Map<Identifier, Collection<TagFinalEntriesValueGetter.FinalEntry>> command_crafter$finalEntries = new HashMap<>();

    @Inject(
            method = "load",
            at = @At("HEAD")
    )
    private void command_crafter$clearTagFileLines(ResourceManager resourceManager, CallbackInfoReturnable<Map<Identifier, List<TagLoader.EntryWithSource>>> cir) {
        command_crafter$tagFileLines.clear();
    }

    @ModifyVariable(
            method = "load",
            at = @At("STORE")
    )
    private Map.Entry<Identifier, List<Resource>> command_crafter$saveTagFileContents(Map.Entry<Identifier, List<Resource>> resources) {
        var id = resources.getKey();
        for(var resource : resources.getValue()) {
            try {
                command_crafter$tagFileLines.put(
                        new PackagedId(id, PackagedId.Companion.getPackIdWithoutPrefix(resource.sourcePackId())),
                        new FileMappingInfo(resource.openAsReader().lines().toList(), new SplitProcessedInputCursorMapper(), 0, 0, new Int2ObjectLinkedOpenHashMap<>(), new Object2ObjectLinkedOpenHashMap<>())
                );
            } catch (IOException ignored) {
                // The IO error will be handled once the TagGroupLoader tries to open the file as well
            } 
        }
        return resources;
    }

    @WrapOperation(
            method = "load",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/Codec;parse(Lcom/mojang/serialization/Dynamic;)Lcom/mojang/serialization/DataResult;",
                    remap = false
            )
    )
    private DataResult<TagFile> command_crafter$provideElementRanges(Codec<TagFile> instance, Dynamic<JsonElement> dynamic, Operation<DataResult<TagFile>> op, @Local Resource resource) throws IOException {
        try {
            var contentReader = new StringReader(resource.openAsReader().lines().collect(Collectors.joining("\n")));
            var stringRangeTree = new StringRangeTreeJsonReader(contentReader).read(Strictness.LENIENT, false);
            // Copy to HashMap because the StringRangeTree doesn't contain the instances that are passed to the codec, so it can't use the IdentityHashMap
            FunctionTagDebugHandler.Companion.getTAG_PARSING_ELEMENT_RANGES().set(new HashMap<>(stringRangeTree.getRanges()));
            return op.call(instance, dynamic);
        } finally {
            FunctionTagDebugHandler.Companion.getTAG_PARSING_ELEMENT_RANGES().remove();
        }
    }
    
    @Inject(
            method = "build",
            at = @At("HEAD")
    )
    private void command_crafter$storeParsedTags(Map<Identifier, List<TagLoader.EntryWithSource>> parsedTags, CallbackInfoReturnable<Map<Identifier, Collection<T>>> cir) {
        command_crafter$parsedTags.set(parsedTags);
        command_crafter$finalEntries.clear();
    }

    @Inject(
            method = "build",
            at = @At("RETURN")
    )
    private void command_crafter$buildBreakpointParsers(Map<Identifier, Collection<T>> finalEntries, CallbackInfoReturnable<Map<Identifier, Collection<T>>> cir) {
        command_crafter$parsedTags.remove();
    }

    @Inject(
            method = "method_51476",
            at = @At("HEAD")
    )
    private void command_crafter$createFinalEntriesForTagDebugging(TagEntry.Lookup<T> valueGetter, Map<Identifier, Collection<T>> map, Identifier id, @Coerce Object dependencies, CallbackInfo ci) {
        if(!directory.equals(FunctionTagDebugHandler.Companion.getTAG_PATH())) return;
        TagFinalEntriesValueGetter.Companion.getOrCreateFinalEntriesForTag(
                id,
                command_crafter$parsedTags.get(),
                command_crafter$finalEntries
        );
    }

    @NotNull
    @Override
    public Map<Identifier, Collection<TagFinalEntriesValueGetter.FinalEntry>> command_crafter$getFinalTags() {
        return command_crafter$finalEntries;
    }

    @NotNull
    @Override
    public Map<PackagedId, FileMappingInfo> command_crafter$getFileContent() {
        return command_crafter$tagFileLines;
    }
}
