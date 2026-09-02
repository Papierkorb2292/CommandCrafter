package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.serialization.Codec;
import net.minecraft.commands.arguments.ResourceOrIdArgument;
import net.minecraft.core.Registry;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.parsing.packrat.commands.Grammar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ResourceOrIdArgument.class)
public interface ResourceOrIdArgumentAccessor {
    @Accessor
    Codec<Object> getCodec();
    @Accessor
    Grammar<ResourceOrIdArgument.Result<?, Tag>> getGrammar();
    @Accessor
    ResourceKey<? extends Registry<Object>> getRegistryKey();
}
