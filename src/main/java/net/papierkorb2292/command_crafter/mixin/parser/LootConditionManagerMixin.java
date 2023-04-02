package net.papierkorb2292.command_crafter.mixin.parser;

import com.google.common.collect.ImmutableMap;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.condition.LootConditionManager;
import net.minecraft.util.Identifier;
import net.papierkorb2292.command_crafter.parser.ParsedResourceCreator;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(LootConditionManager.class)
public class LootConditionManagerMixin implements ParsedResourceCreator.VanillaReourceContainer<LootCondition> {

    @Shadow private Map<Identifier, LootCondition> conditions;

    @Override
    public void command_crafter$addAllResources(@NotNull Map<Identifier, ? extends LootCondition> newResources) {
        var builder = new ImmutableMap.Builder<Identifier, LootCondition>();
        builder.putAll(conditions);
        builder.putAll(newResources);
        conditions = builder.build();
    }
}
