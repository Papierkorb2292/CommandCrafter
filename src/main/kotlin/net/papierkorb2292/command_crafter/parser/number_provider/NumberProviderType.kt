package net.papierkorb2292.command_crafter.parser.number_provider

import com.mojang.serialization.Codec
import com.mojang.serialization.Decoder
import net.minecraft.core.Holder
import net.minecraft.core.HolderSet
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import kotlin.jvm.optionals.getOrNull

class NumberProviderType<TNumberProvider: Any>(
    val functions: List<NumberProviderFunction<TNumberProvider>>,
    val infixOperations: List<NumberProviderInfix<TNumberProvider>>,
    val numberDecoder: Decoder<out TNumberProvider>,
    val constants: List<Pair<String, TNumberProvider>>,
    val negateFactory: (Holder<TNumberProvider>) -> TNumberProvider,
    val referenceWrapper: (Holder<TNumberProvider>) -> TNumberProvider,
    val inlineCodec: Codec<out TNumberProvider>,
    val registryKey: ResourceKey<Registry<TNumberProvider>>,
)

// Repeated sums or products can be merged into one number provider. The operation is assumed to be commutative, but not associative.
// Note that this can't be done with subtraction or division, because they only take one input on the right side.
// Their input can not be interchanged for a new sum/product by the parser (unless the user specified a sum/product in parentheses),
// because for floats it would introduce rounding errors and for ints it would introduce overflow errors. No number providers are associative for that same reason.
inline fun <TNumberProvider : Any, reified TProviderCompound : TNumberProvider> optimizeBinarySetInput(
    crossinline factory: (HolderSet<TNumberProvider>) -> TProviderCompound,
    crossinline getter: (TProviderCompound) -> HolderSet<TNumberProvider>,
): (left: Holder<TNumberProvider>, right: Holder<TNumberProvider>) -> TNumberProvider = optimizedFactory@{ left, right ->
    val unwrappedLeft = left.unwrap().right().getOrNull()
    if(unwrappedLeft is TProviderCompound) {
        // Note: Even if right is also a TCompoundProvider, it is not possible to merge them due to missing associativity
        val leftInputs = getter(unwrappedLeft).unwrap()
        if(leftInputs.right().isPresent)
            return@optimizedFactory factory(HolderSet.direct(leftInputs.right().get() + right))
    }
    val unwrappedRight = right.unwrap().right().getOrNull()
    if(unwrappedRight is TProviderCompound) {
        val rightInputs = getter(unwrappedRight).unwrap()
        if(rightInputs.right().isPresent)
            return@optimizedFactory factory(HolderSet.direct(rightInputs.right().get() + left))
    }
    factory(HolderSet.direct(left, right))
}