package net.papierkorb2292.command_crafter.mixin.parser;

import com.google.common.collect.ImmutableMap;
import net.minecraft.loot.LootManager;
import net.minecraft.loot.LootTable;
import net.minecraft.util.Identifier;
import net.papierkorb2292.command_crafter.parser.ParsedResourceCreator;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(LootManager.class)
public class LootManagerMixin implements ParsedResourceCreator.VanillaReourceContainer<LootTable> {


    @Shadow private Map<Identifier, LootTable> tables;

    @Override
    public void command_crafter$addAllResources(@NotNull Map<Identifier, ? extends LootTable> newResources) {
        var builder = ImmutableMap.<Identifier, LootTable>builder();
        builder.putAll(tables);
        builder.putAll(newResources);
        tables = builder.build();
    }
}
