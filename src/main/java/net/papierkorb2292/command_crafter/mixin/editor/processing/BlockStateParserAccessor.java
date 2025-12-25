package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.world.level.block.Block;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.HolderLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BlockStateParser.class)
public interface BlockStateParserAccessor {

    @Invoker("<init>")
    static BlockStateParser callInit(HolderLookup<Block> registryWrapper, StringReader reader, boolean allowTag, boolean allowSnbt) {
        throw new AssertionError();
    }

    @Invoker
    void callParse() throws CommandSyntaxException;
}
