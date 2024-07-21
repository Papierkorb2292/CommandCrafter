package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.mojang.datafixers.util.Pair;
import net.minecraft.registry.tag.TagGroupLoader;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.function.CommandFunction;
import net.minecraft.server.function.FunctionLoader;
import net.minecraft.util.Identifier;
import net.papierkorb2292.command_crafter.editor.debugger.BreakpointParser;
import net.papierkorb2292.command_crafter.editor.debugger.helper.FinalTagRangeEntriesProvider;
import net.papierkorb2292.command_crafter.editor.debugger.helper.IdentifiedBreakpointParserProvider;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags.FunctionTagBreakpointLocation;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags.RangeFunctionTagBreakpointParser;
import net.papierkorb2292.command_crafter.parser.FileMappingInfo;
import net.papierkorb2292.command_crafter.parser.helper.SplitProcessedInputCursorMapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(FunctionLoader.class)
public class FunctionLoaderMixin implements IdentifiedBreakpointParserProvider<FunctionTagBreakpointLocation> {

    @Shadow @Final private TagGroupLoader<CommandFunction<ServerCommandSource>> tagLoader;
    private final Map<Identifier, RangeFunctionTagBreakpointParser> command_crafter$tagBreakpointParsers = new HashMap<>();

    @Inject(
            method = "method_29453",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/registry/tag/TagGroupLoader;buildGroup(Ljava/util/Map;)Ljava/util/Map;",
                    shift = At.Shift.AFTER
            )
    )
    private void command_crafter$createTagBreakpointParsers(Pair<?, ?> intermediate, CallbackInfo ci) {
        command_crafter$tagBreakpointParsers.clear();

        var finalTags = Objects.requireNonNull(((FinalTagRangeEntriesProvider) tagLoader).command_crafter$getFinalRangeTags());
        var tagEntryRangesFilesPerId = new HashMap<Identifier, List<RangeFunctionTagBreakpointParser.TagEntriesRangeFile>>();

        for(var finalTag : finalTags.entrySet()) {
            var packagedId = finalTag.getKey();
            var tagInfo = finalTag.getValue();
            var lines = tagInfo.getFirst();
            var tagEntries = tagInfo.getSecond();
            var tagEntriesRangeFile = new RangeFunctionTagBreakpointParser.TagEntriesRangeFile(
                    packagedId.getPackPath(),
                    new FileMappingInfo(
                            lines,
                            new SplitProcessedInputCursorMapper(),
                            0,
                            0
                    ),
                    tagEntries.entrySet().stream().map(entry -> {
                        var packPath = entry.getKey();
                        var entryIndices = entry.getValue();
                        return new RangeFunctionTagBreakpointParser.FileEntry(
                                packPath,
                                new FunctionTagBreakpointLocation(entryIndices)
                        );
                    }).toList()
            );

            tagEntryRangesFilesPerId.computeIfAbsent(
                    packagedId.getResourceId(),
                    key -> new ArrayList<>()
            ).add(tagEntriesRangeFile);
        }

        for(var tag : tagEntryRangesFilesPerId.entrySet()) {
            var tagId = tag.getKey();
            var tagEntryRangesFiles = tag.getValue();
            command_crafter$tagBreakpointParsers.put(
                    tagId,
                    new RangeFunctionTagBreakpointParser(tagEntryRangesFiles, null)
            );
        }
    }

    @Nullable
    @Override
    public BreakpointParser<FunctionTagBreakpointLocation> command_crafter$getBreakpointParser(@NotNull Identifier id) {
        return command_crafter$tagBreakpointParsers.get(id);
    }
}
