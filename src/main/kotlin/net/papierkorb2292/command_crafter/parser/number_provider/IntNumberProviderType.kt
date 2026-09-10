package net.papierkorb2292.command_crafter.parser.number_provider

import net.minecraft.core.HolderSet
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.storage.loot.providers.number.ints.*

val INT_NUMBER_PROVIDER_TYPE = NumberProviderType<ContextIntProvider>(
    listOf(
        NumberProviderFunction("pow", 2) { args -> Power(args[0], args[1]) },
        NumberProviderFunction("random", 2) { args -> UniformGenerator(args[0], args[1]) },
        NumberProviderFunction("floorMod", 2) { args -> FloorModulus(args[0], args[1]) },
        NumberProviderFunction("floorDiv", 2) { args -> FloorQuotient(args[0], args[1]) },
        NumberProviderFunction("abs", 1) { args -> Absolute(args[0]) },
        NumberProviderFunction("max") { args -> Maximum(HolderSet.direct(args)) },
        NumberProviderFunction("min") { args -> Minimum(HolderSet.direct(args)) },
        NumberProviderFunction("avg") { args -> Average(HolderSet.direct(args)) },
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
)