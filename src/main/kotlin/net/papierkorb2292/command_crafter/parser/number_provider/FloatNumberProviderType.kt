package net.papierkorb2292.command_crafter.parser.number_provider

import net.minecraft.core.HolderSet
import net.minecraft.core.registries.Registries
import net.minecraft.util.Mth
import net.minecraft.world.level.storage.loot.LootContext
import net.minecraft.world.level.storage.loot.providers.number.floats.*
import net.papierkorb2292.command_crafter.Util

val FLOAT_NUMBER_PROVIDER_TYPE = NumberProviderType<ContextFloatProvider>(
    listOf(
        NumberProviderFunction("pow", 2, true) { args -> Power(args[0], args[1]) },
        NumberProviderFunction("random", 2, false) { args -> UniformGenerator(args[0], args[1]) },
        NumberProviderFunction("floor", 1, true) { args -> Floor(args[0]) },
        NumberProviderFunction("ceil", 1, true) { args -> Ceiling(args[0]) },
        NumberProviderFunction("round", 1, true) { args -> Round(args[0]) },
        NumberProviderFunction("abs", 1, true) { args -> Absolute(args[0]) },
        NumberProviderFunction("sin", 1, true) { args -> Sine(args[0]) },
        NumberProviderFunction("cos", 1, true) { args -> Cosine(args[0]) },
        NumberProviderFunction("sqrt", 1, true) { args -> SquareRoot(args[0]) },
        NumberProviderFunction("max", true) { args -> Maximum(HolderSet.direct(args)) },
        NumberProviderFunction("min", true) { args -> Minimum(HolderSet.direct(args)) },
        NumberProviderFunction("avg", true) { args -> Average(HolderSet.direct(args)) },
        NumberProviderFunction("length", true) { args -> Length(HolderSet.direct(args)) },
    ),
    listOf(
        NumberProviderInfix('+', 4, optimizeBinarySetInput(::Sum, Sum::inputs)),
        NumberProviderInfix('-', 3) { left, right -> Difference(left, right) },
        NumberProviderInfix('*', 3, optimizeBinarySetInput(::Product, Product::inputs)),
        NumberProviderInfix('/', 3) { left, right -> Quotient(left, right) },
        NumberProviderInfix('%', 3) { left, right -> Modulus(left, right) },
    ),
    ConstantValue.INLINE_CODEC,
    listOf("PI" to ConstantValue(Mth.PI), "E" to ConstantValue(Math.E.toFloat())),
    ::Negate,
    { Sum(HolderSet.direct(it)) },
    ContextFloatProviders.DIRECT_CODEC,
    Registries.CONTEXT_FLOAT_PROVIDER,
    { it is ConstantValue },
    { constantProvider ->
        try {
            ConstantValue(constantProvider.getFloat(Util.nullIsFine<LootContext>(null)))
        } catch(_: NullPointerException) {
            constantProvider // Shouldn't happen since the provider shouldn't depend on the context, but just in case
        }
    }
)