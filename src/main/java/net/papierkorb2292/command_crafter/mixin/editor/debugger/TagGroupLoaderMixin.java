package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.mojang.brigadier.context.StringRange;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import kotlin.Pair;
import net.minecraft.registry.tag.TagEntry;
import net.minecraft.registry.tag.TagFile;
import net.minecraft.registry.tag.TagGroupLoader;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.papierkorb2292.command_crafter.editor.PackagedId;
import net.papierkorb2292.command_crafter.editor.debugger.helper.FinalTagRangeEntriesProvider;
import net.papierkorb2292.command_crafter.editor.debugger.helper.StringRangeContainer;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags.FunctionTagDebugHandler;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags.TagFinalEntriesValueGetter;
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTreeJsonReader;
import net.papierkorb2292.command_crafter.string_range_gson.Strictness;
import org.jetbrains.annotations.Nullable;
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
import java.util.*;

@Mixin(TagGroupLoader.class)
public class TagGroupLoaderMixin<T> implements FinalTagRangeEntriesProvider {

    @Shadow @Final private String dataType;
    private final ThreadLocal<Map<Identifier, List<TagGroupLoader.TrackedEntry>>> command_crafter$parsedTags = new ThreadLocal<>();
    private final ThreadLocal<Identifier> command_crafter$resolveTagId = new ThreadLocal<>();
    private final Map<PackagedId, Map<StringRange, Map<Identifier, Integer>>> command_crafter$tagEntryIndices = new HashMap<>();
    private final Map<PackagedId, List<String>> command_crafter$tagFileLines = new HashMap<>();
    private final Map<Identifier, Collection<Pair<Identifier, TagGroupLoader.TrackedEntry>>> command_crafter$finalEntriesCache = new HashMap<>();

    @ModifyVariable(
            method = "loadTags",
            at = @At("STORE")
    )
    private Map.Entry<Identifier, List<Resource>> command_crafter$saveTagFileContents(Map.Entry<Identifier, List<Resource>> resources) {
        var id = resources.getKey();
        for(var resource : resources.getValue()) {
            try {
                command_crafter$tagFileLines.put(
                        new PackagedId(id, PackagedId.Companion.getPackIdWithoutPrefix(resource.getPackId())),
                        resource.getReader().lines().toList()
                );
            } catch (IOException ignored) {
                // The IO error will be handled once the TagGroupLoader tries to open the file as well
            } 
        }
        return resources;
    }

    @WrapOperation(
            method = "loadTags",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/Codec;parse(Lcom/mojang/serialization/Dynamic;)Lcom/mojang/serialization/DataResult;",
                    remap = false
            )
    )
    private DataResult<TagFile> command_crafter$provideElementRanges(Codec<TagFile> instance, Dynamic<JsonElement> dynamic, Operation<DataResult<TagFile>> op, @Local Resource resource) throws IOException {
        try {
            var stringRangeTree = new StringRangeTreeJsonReader(resource.getReader()).read(Strictness.LENIENT, false);
            // Copy to HashMap because the StringRangeTree doesn't contain the instances that are passed to the codec, so it can't use the IdentityHashMap
            FunctionTagDebugHandler.Companion.getTAG_PARSING_ELEMENT_RANGES().set(new HashMap<>(stringRangeTree.getRanges()));
            return op.call(instance, dynamic);
        } finally {
            FunctionTagDebugHandler.Companion.getTAG_PARSING_ELEMENT_RANGES().remove();
        }
    }
    
    @Inject(
            method = "buildGroup",
            at = @At("HEAD")
    )
    private void command_crafter$storeParsedTags(Map<Identifier, List<TagGroupLoader.TrackedEntry>> parsedTags, CallbackInfoReturnable<Map<Identifier, Collection<T>>> cir) {
        command_crafter$parsedTags.set(parsedTags);
        command_crafter$tagEntryIndices.clear();
    }

    @Inject(
            method = "buildGroup",
            at = @At("RETURN")
    )
    private void command_crafter$buildBreakpointParsers(Map<Identifier, Collection<T>> finalEntries, CallbackInfoReturnable<Map<Identifier, Collection<T>>> cir) {
        command_crafter$parsedTags.remove();
        command_crafter$resolveTagId.remove();
        command_crafter$finalEntriesCache.clear();
    }

    @Inject(
            method = "method_51476",
            at = @At("HEAD")
    )
    private void command_crafter$storeTagId(TagEntry.ValueGetter<T> valueGetter, Map<Identifier, Collection<T>> map, Identifier id, @Coerce Object dependencies, CallbackInfo ci) {
        command_crafter$resolveTagId.set(id);
    }

    @ModifyVariable(
            method = "resolveAll",
            at = @At("STORE")
    )
    private TagGroupLoader.TrackedEntry command_crafter$storeEntryRangesForTagDebugging(TagGroupLoader.TrackedEntry trackedEntry, @Share("entryCount") LocalIntRef entryCount) {
        if(!dataType.equals(FunctionTagDebugHandler.Companion.getTAG_PATH()))
            return trackedEntry;
        trackedEntry.entry().resolve(new TagFinalEntriesValueGetter(command_crafter$resolveTagId.get(), trackedEntry, command_crafter$parsedTags.get(), command_crafter$finalEntriesCache), fileEntry -> {
            var sourceFileEntries = command_crafter$tagEntryIndices.computeIfAbsent(
                    new PackagedId(fileEntry.getFirst(), PackagedId.Companion.getPackIdWithoutPrefix(fileEntry.getSecond().source())),
                    key -> new HashMap<>()
            );

            var range = ((StringRangeContainer)fileEntry.getSecond().entry()).command_crafter$getRange();
            Objects.requireNonNull(range, "Range of parsed tag entry is null");
            var entryIndices = sourceFileEntries.computeIfAbsent(range, key -> new HashMap<>());

            entryIndices.put(command_crafter$resolveTagId.get(), entryCount.get());
            entryCount.set(entryCount.get() + 1);
        });
        return trackedEntry;
    }

    @Nullable
    @Override
    public Map<PackagedId, Pair<List<String>, Map<StringRange, Map<Identifier, Integer>>>> command_crafter$getFinalRangeTags() {
        var result = new HashMap<PackagedId, Pair<List<String>, Map<StringRange, Map<Identifier, Integer>>>>();
        for(var entry : command_crafter$tagEntryIndices.entrySet()) {
            var id = entry.getKey();
            var tagEntries = entry.getValue();
            var content = Objects.requireNonNull(command_crafter$tagFileLines.get(id));
            result.put(id, new Pair<>(content, tagEntries));
        }
        return result;
    }
}
