package net.papierkorb2292.command_crafter.parser.number_provider

import net.minecraft.core.HolderSet
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.storage.loot.LootContext
import net.minecraft.world.level.storage.loot.providers.number.ints.*
import net.papierkorb2292.command_crafter.Util

val INT_NUMBER_PROVIDER_TYPE = NumberProviderType<ContextIntProvider>(
    listOf(
        NumberProviderFunction("pow", 2, true) { args -> Power(args[0], args[1]) },
        NumberProviderFunction("random", 2, false) { args -> UniformGenerator(args[0], args[1]) },
        NumberProviderFunction("floorMod", 2, true) { args -> FloorModulus(args[0], args[1]) },
        NumberProviderFunction("floorDiv", 2, true) { args -> FloorQuotient(args[0], args[1]) },
        NumberProviderFunction("abs", 1, true) { args -> Absolute(args[0]) },
        NumberProviderFunction("max", true) { args -> Maximum(HolderSet.direct(args)) },
        NumberProviderFunction("min", true) { args -> Minimum(HolderSet.direct(args)) },
        NumberProviderFunction("avg", true) { args -> Average(HolderSet.direct(args)) },
    ),
    listOf(
        NumberProviderInfix('+', 4, optimizeBinarySetInput(::Sum, Sum::inputs)),
        NumberProviderInfix('-', 3) { left, right -> Difference(left, right) },
        NumberProviderInfix('*', 3, optimizeBinarySetInput(::Product, Product::inputs)),
        NumberProviderInfix('/', 3) { left, right -> Quotient(left, right) },
        NumberProviderInfix('%', 3) { left, right -> Modulus(left, right) },
    ),
    ConstantValue.INLINE_CODEC,
    listOf(),
    ::Negate,
    { Sum(HolderSet.direct(it)) },
    ContextIntProviders.DIRECT_CODEC,
    Registries.CONTEXT_INT_PROVIDER,
    { it is ConstantValue },
    { constantProvider ->
        try {
            ConstantValue(constantProvider.getInt(Util.nullIsFine<LootContext>(null)))
        } catch(_: NullPointerException) {
            constantProvider // Shouldn't happen since the provider shouldn't depend on the context, but just in case
        }
    }
)