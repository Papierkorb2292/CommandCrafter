package net.papierkorb2292.command_crafter.editor.debugger.helper

import com.mojang.datafixers.util.Either
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.papierkorb2292.command_crafter.networking.EVALUATE_RESPONSE_PACKET_CODEC
import org.eclipse.lsp4j.debug.EvaluateArguments
import org.eclipse.lsp4j.debug.EvaluateResponse
import java.util.concurrent.CompletableFuture

interface EvaluationProvider {
    companion object {
        val DUMMY = object : EvaluationProvider {
            override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluationResult?> {
                return CompletableFuture.completedFuture(null)
            }
        }

        val EVALUATION_FAILED_THROWABLE_PACKET_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            { it.message!! },
            ::EvaluationFailedThrowable
        )

        val EVALUATION_RESULT_PACKET_CODEC = StreamCodec.composite(
            ByteBufCodecs.either(
                EVALUATE_RESPONSE_PACKET_CODEC,
                EVALUATION_FAILED_THROWABLE_PACKET_CODEC
            ),
            EvaluationResult::response,
            ::EvaluationResult
        )

        fun combine(providers: List<EvaluationProvider>) = object : EvaluationProvider {
            override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluationResult?> {
                val futures = providers.map { it.evaluate(args) }.toTypedArray()
                return CompletableFuture.allOf(*futures).thenApply {
                    futures.firstNotNullOfOrNull { it.get() }
                }
            }
        }

        fun EvaluationProvider?.withAlternativeForNull(other: EvaluationProvider?): EvaluationProvider {
            if(this == null)
                return other ?: DUMMY
            if(other == null)
                return this
            return object : EvaluationProvider {
                override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluationResult?>
                    = this@withAlternativeForNull.evaluate(args).thenCompose { result ->
                        if(result != null)
                            CompletableFuture.completedFuture(result)
                        else
                            other.evaluate(args)
                    }
            }
        }

        fun delegating(provider: (EvaluateArguments) -> EvaluationProvider?) = object : EvaluationProvider {
            override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluationResult?> =
                (provider(args) ?: DUMMY).evaluate(args)
        }

        fun EvaluationProvider.needsLocation(): EvaluationProvider {
            return object : EvaluationProvider {
                override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluationResult?> {
                    val provider = if(args.line != null && args.column != null) this@needsLocation else DUMMY
                    return provider.evaluate(args)
                }
            }
        }

        fun EvaluateArguments.copy() = EvaluateArguments().also {
            it.expression = this.expression
            it.context = this.context
            it.frameId = this.frameId
            it.line = this.line
            it.column = this.column
            it.source = this.source
            it.format = this.format
        }

        fun createError(error: String) = EvaluationResult(EvaluationFailedThrowable(error))
        fun createResponse(response: EvaluateResponse) = EvaluationResult(response)
    }

    fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluationResult?>

    class EvaluationResult(
        val response: Either<EvaluateResponse, EvaluationFailedThrowable>
    ) {
        constructor(response: EvaluateResponse) : this(Either.left(response))
        constructor(error: EvaluationFailedThrowable) : this(Either.right(error))

        fun toFuture(): CompletableFuture<EvaluateResponse>
                = response.map(
            CompletableFuture<EvaluateResponse>::completedFuture,
            CompletableFuture<EvaluateResponse>::failedFuture
        )
    }

    class EvaluationFailedThrowable(message: String, cause: Throwable? = null): Throwable(message, cause)
}