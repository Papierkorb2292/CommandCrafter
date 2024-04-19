package net.papierkorb2292.command_crafter.editor.debugger.helper

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.CommandContextBuilder
import com.mojang.brigadier.context.ContextChain
import com.mojang.brigadier.context.StringRange
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.MinecraftServer
import net.minecraft.server.function.Procedure
import net.minecraft.util.Identifier
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
    = Identifier(namespace, "$path$extension")

fun PacketByteBuf.writeBreakpoint(breakpoint: Breakpoint) {
    writeVarInt(breakpoint.id)
    writeBoolean(breakpoint.isVerified)
    writeNullableString(breakpoint.message)
    writeNullable(breakpoint.source, PacketByteBuf::writeSource)
    writeNullableVarInt(breakpoint.line)
    writeNullableVarInt(breakpoint.column)
    writeNullableVarInt(breakpoint.endLine)
    writeNullableVarInt(breakpoint.endColumn)
}

fun PacketByteBuf.readBreakpoint(): Breakpoint {
    val breakpoint = Breakpoint()
    breakpoint.id = readVarInt()
    breakpoint.isVerified = readBoolean()
    breakpoint.message = readNullableString()
    breakpoint.source = readNullable(PacketByteBuf::readSource)
    breakpoint.line = readNullableVarInt()
    breakpoint.column = readNullableVarInt()
    breakpoint.endLine = readNullableVarInt()
    breakpoint.endColumn = readNullableVarInt()
    return breakpoint
}

fun PacketByteBuf.writeNullableValueFormat(valueFormat: ValueFormat?) {
    writeNullableBool(valueFormat?.hex)
}

fun PacketByteBuf.readNullableValueFormat(): ValueFormat? {
    return readNullableBool()?.let {
        ValueFormat().apply { hex = it }
    }
}

fun PacketByteBuf.writeSource(source: Source) {
    writeString(source.name)
    writeNullableString(source.path)
    writeNullableInt(source.sourceReference)
    writeNullableInt(source.presentationHint?.ordinal)
    writeNullableString(source.origin)
    val sources = source.sources
    if(sources == null) {
        writeBoolean(false)
    } else {
        writeBoolean(true)
        writeInt(sources.size)
        for(s in sources) {
            writeSource(s)
        }
    }
}

fun PacketByteBuf.readSource(): Source {
    val source = Source()
    source.name = readString()
    source.path = readNullableString()
    source.sourceReference = readNullableInt()
    source.presentationHint = readNullableInt()?.run { SourcePresentationHint.values()[this] }
    source.origin = readNullableString()
    if(readBoolean()) {
        source.sources = Array(readInt()) {
            readSource()
        }
    }
    return source
}

operator fun StringRange.plus(value: Int) = StringRange(start + value, end + value)
operator fun StringRange.minus(value: Int) = StringRange(start - value, end - value)

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

fun Procedure<*>.getOriginalId() = (this as? ProcedureOriginalIdContainer)?.`command_crafter$getOriginalId`() ?: id()