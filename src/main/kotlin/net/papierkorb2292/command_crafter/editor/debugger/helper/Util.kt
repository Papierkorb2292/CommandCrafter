package net.papierkorb2292.command_crafter.editor.debugger.helper

import com.mojang.brigadier.context.CommandContextBuilder
import com.mojang.brigadier.context.StringRange
import net.minecraft.network.PacketByteBuf
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.networking.*
import org.eclipse.lsp4j.debug.Breakpoint
import org.eclipse.lsp4j.debug.ValueFormat

operator fun <S> CommandContextBuilder<S>.get(index: Int): CommandContextBuilder<S>? {
    var context = this
    for(i in 0 until index) {
        context = context.child ?: return null
    }
    return context
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

operator fun StringRange.minus(value: Int) = StringRange(start - value, end - value)