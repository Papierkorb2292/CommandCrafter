package net.papierkorb2292.command_crafter.editor.debugger.helper

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.CommandContextBuilder
import com.mojang.brigadier.context.ContextChain
import com.mojang.brigadier.context.StringRange
import net.minecraft.network.PacketByteBuf
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

fun Identifier.removeExtension(extension: String)
    = if(!path.endsWith(extension)) null
        else Identifier.of(namespace, path.substring(0, path.length - extension.length))

fun Identifier.withExtension(extension: String)
    = Identifier(namespace, "$path$extension")

fun PacketByteBuf.writeBreakpoint(breakpoint: Breakpoint) {
    writeVarInt(breakpoint.id)
    writeBoolean(breakpoint.isVerified)

    writeNullableInt(breakpoint.line)
    writeNullableInt(breakpoint.endLine)
    writeNullableInt(breakpoint.column)
    writeNullableInt(breakpoint.endColumn)
    writeNullableString(breakpoint.message)
}

fun PacketByteBuf.readBreakpoint(): Breakpoint {
    val breakpoint = Breakpoint()
    breakpoint.id = readVarInt()
    breakpoint.isVerified = readBoolean()

    breakpoint.line = readNullableInt()
    breakpoint.endLine = readNullableInt()
    breakpoint.column = readNullableInt()
    breakpoint.endColumn = readNullableInt()
    breakpoint.message = readNullableString()
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
    writeInt(source.sources.size)
    for(s in source.sources) {
        writeSource(s)
    }
}

fun PacketByteBuf.readSource(): Source {
    val source = Source()
    source.name = readString()
    source.path = readNullableString()
    source.sourceReference = readNullableInt()
    source.presentationHint = readNullableInt()?.run { SourcePresentationHint.values()[this] }
    source.origin = readNullableString()
    source.sources = Array(readInt()) {
        readSource()
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