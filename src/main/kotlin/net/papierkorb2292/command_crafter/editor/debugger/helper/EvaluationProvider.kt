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