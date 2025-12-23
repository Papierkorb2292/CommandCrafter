package net.papierkorb2292.command_crafter.editor.debugger.helper

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.CommandContextBuilder
import com.mojang.brigadier.context.ContextChain
import com.mojang.brigadier.context.StringRange
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.MinecraftServer
import net.minecraft.server.function.Procedure
import net.minecraft.util.Identifier
import net.minecraft.util.math.MathHelper
import net.papierkorb2292.command_crafter.mixin.editor.debugger.ContextChainAccessor
import net.papierkorb2292.command_crafter.networking.*
import org.eclipse.lsp4j.debug.*

operator fun <S> CommandContextBuilder<S>.get(index: Int): CommandContextBuilder<S>? {
    var context = this
    for(i in 0 until index) {
        context = context.child ?: return null
    }
    return context
}

operator fun <S> ContextChain<S>.get(index: Int): CommandContext<S>? {
    @Suppress("UNCHECKED_CAST")
    val accessor = this as ContextChainAccessor<S>
    if(accessor.modifiers.size > index) return accessor.modifiers[index]
    if(accessor.modifiers.size == index) return accessor.executable
    return null
}

fun <S> CommandContext<S>.getExcludeEmpty(index: Int): CommandContext<S>? {
    var context = this
    for(i in 0 until index) {
        context = context.child ?: return null
        while(context.redirectModifier == null && context.command == null) {
            context = context.child ?: return null
        }
    }
    return context
}

fun <S> CommandContext<S>.isDebuggable(): Boolean {
    return if(this.child == null) this.command != null else this.redirectModifier != null
}

fun Identifier.removeExtension(extension: String)
    = if(!path.endsWith(extension)) null
        else Identifier.of(namespace, path.substring(0, path.length - extension.length))

fun Identifier.withExtension(extension: String)
    = Identifier.of(namespace, "$path$extension")

operator fun StringRange.plus(value: Int) = StringRange(start + value, end + value)
operator fun StringRange.minus(value: Int) = StringRange(start - value, end - value)
fun StringRange.clamp(clampRange: StringRange) = StringRange(
    MathHelper.clamp(start, clampRange.start, clampRange.end),
    MathHelper.clamp(end, clampRange.start, clampRange.end)
)

fun SourceBreakpoint.copy(): SourceBreakpoint {
    val breakpoint = SourceBreakpoint()
    breakpoint.line = line
    breakpoint.column = column
    breakpoint.condition = condition
    breakpoint.hitCondition = hitCondition
    breakpoint.logMessage = logMessage
    return breakpoint
}

fun MinecraftServer.getDebugManager() = (this as ServerDebugManagerContainer).`command_crafter$getServerDebugManager`()

fun Procedure<*>.getOriginalId(): Identifier = (this as? ProcedureOriginalIdContainer)?.`command_crafter$getOriginalId`() ?: id()