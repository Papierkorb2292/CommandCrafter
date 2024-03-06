package net.papierkorb2292.command_crafter.mixin.parser;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import kotlin.Pair;
import net.minecraft.loot.LootDataKey;
import net.minecraft.loot.LootDataType;
import net.minecraft.loot.LootManager;
import net.minecraft.util.Identifier;
import net.papierkorb2292.command_crafter.parser.ParsedResourceCreator;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(LootManager.class)
public class LootManagerMixin implements ParsedResourceCreator.VanillaResourceContainer<Pair<LootDataType<?>, ?>> {


    @Shadow private Map<LootDataKey<?>, ?> keyToValue;

    @Shadow private Multimap<LootDataType<?>, Identifier> typeToIds;

    @Override
    public void command_crafter$addAllResources(@NotNull Map<Identifier, ? extends Pair<LootDataType<?>, ?>> newResources) {
        ImmutableMap.Builder<LootDataKey<?>, Object> keyToValueBuilder = ImmutableMap.builder();
        keyToValueBuilder.putAll(keyToValue);
        ImmutableMultimap.Builder<LootDataType<?>, Identifier> typeToIdsBuilder = ImmutableMultimap.builder();
        typeToIdsBuilder.putAll(typeToIds);

        for (var entry : newResources.entrySet()) {
            var type = entry.getValue().getFirst();
            var id = entry.getKey();
            var value = entry.getValue().getSecond();
            keyToValueBuilder.put(new LootDataKey<>(type, id), value);
            typeToIdsBuilder.put(type, id);
        }

        keyToValue = keyToValueBuilder.build();
        typeToIds = typeToIdsBuilder.build();
    }
}
