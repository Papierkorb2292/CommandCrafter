package net.papierkorb2292.command_crafter.mixin.parser;

import com.google.common.collect.ImmutableMap;
import net.minecraft.loot.function.LootFunction;
import net.minecraft.loot.function.LootFunctionManager;
import net.minecraft.util.Identifier;
import net.papierkorb2292.command_crafter.parser.ParsedResourceCreator;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(LootFunctionManager.class)
public class LootFunctionManagerMixin implements ParsedResourceCreator.VanillaReourceContainer<LootFunction> {


    @Shadow private Map<Identifier, LootFunction> functions;

    @Override
    public void command_crafter$addAllResources(@NotNull Map<Identifier, ? extends LootFunction> newResources) {
        var builder = ImmutableMap.<Identifier, LootFunction>builder();
        builder.putAll(functions);
        builder.putAll(newResources);
        functions = builder.build();
    }
}
