package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.block.Block;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.registry.RegistryWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BlockArgumentParser.class)
public interface BlockArgumentParserAccessor {

    @Invoker("<init>")
    static BlockArgumentParser callInit(RegistryWrapper<Block> registryWrapper, StringReader reader, boolean allowTag, boolean allowSnbt) {
        throw new AssertionError();
    }

    @Invoker
    void callParse() throws CommandSyntaxException;
}
