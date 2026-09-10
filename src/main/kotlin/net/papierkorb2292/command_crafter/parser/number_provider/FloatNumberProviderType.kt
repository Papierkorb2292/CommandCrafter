package net.papierkorb2292.command_crafter.parser.number_provider

import net.minecraft.core.HolderSet
import net.minecraft.core.registries.Registries
import net.minecraft.util.Mth
import net.minecraft.world.level.storage.loot.providers.number.floats.*

val FLOAT_NUMBER_PROVIDER_TYPE = NumberProviderType<ContextFloatProvider>(
    listOf(
        NumberProviderFunction("pow", 2) { args -> Power(args[0], args[1]) },
        NumberProviderFunction("random", 2) { args -> UniformGenerator(args[0], args[1]) },
        NumberProviderFunction("floor", 1) { args -> Floor(args[0]) },
        NumberProviderFunction("ceil", 1) { args -> Ceiling(args[0]) },
        NumberProviderFunction("round", 1) { args -> Round(args[0]) },
        NumberProviderFunction("abs", 1) { args -> Absolute(args[0]) },
        NumberProviderFunction("sin", 1) { args -> Sine(args[0]) },
        NumberProviderFunction("cos", 1) { args -> Cosine(args[0]) },
        NumberProviderFunction("sqrt", 1) { args -> SquareRoot(args[0]) },
        NumberProviderFunction("max") { args -> Maximum(HolderSet.direct(args)) },
        NumberProviderFunction("min") { args -> Minimum(HolderSet.direct(args)) },
        NumberProviderFunction("avg") { args -> Average(HolderSet.direct(args)) },
        NumberProviderFunction("length") { args -> Length(HolderSet.direct(args)) },
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
)