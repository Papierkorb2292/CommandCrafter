package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.block.Block;
import net.minecraft.command.argument.BlockStateArgumentType;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingCommandNode;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultCreator;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BlockStateArgumentType.class)
public class BlockStateArgumentTypeMixin implements AnalyzingCommandNode {

    @Shadow
    @Final
    private RegistryWrapper<Block> registryWrapper;

    @Override
    public void command_crafter$analyze(@NotNull CommandContext<ServerCommandSource> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> reader, @NotNull AnalyzingResult result, @NotNull String name) throws CommandSyntaxException {
        var readerCopy = reader.copy();
        readerCopy.setCursor(range.getStart());
        var blockArgumentParser = BlockArgumentParserAccessor.callInit(registryWrapper, readerCopy, false, true);
        ((AnalyzingResultCreator)blockArgumentParser).command_crafter$setAnalyzingResult(result);
        ((BlockArgumentParserAccessor)blockArgumentParser).callParse();
    }
}
