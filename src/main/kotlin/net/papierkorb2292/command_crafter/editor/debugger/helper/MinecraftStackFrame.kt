package net.papierkorb2292.command_crafter.editor.debugger.helper

import net.minecraft.network.PacketByteBuf
import net.papierkorb2292.command_crafter.networking.*
import org.eclipse.lsp4j.debug.Scope
import org.eclipse.lsp4j.debug.StackFramePresentationHint

class MinecraftStackFrame(
    val name: String,
    val visualContext: DebuggerVisualContext,
    val variableScopes: Array<Scope>,
    val presentationHint: StackFramePresentationHint = StackFramePresentationHint.NORMAL
) : ByteBufWritable {
    constructor(buf: PacketByteBuf) : this(
        buf.readString(),
        DebuggerVisualContext(buf),
        Array(buf.readVarInt()) {
            val scope = Scope()
            scope.name = buf.readString()
            scope.variablesReference = buf.readVarInt()
            scope.presentationHint = buf.readNullableString()
            scope.isExpensive = buf.readBoolean()
            scope.namedVariables = buf.readNullableInt()
            scope.indexedVariables = buf.readNullableInt()
            scope.line = buf.readNullableInt()
            scope.column = buf.readNullableInt()
            scope.endLine = buf.readNullableInt()
            scope.endColumn = buf.readNullableInt()
            scope
        },
        buf.readEnumConstant(StackFramePresentationHint::class.java)
    )

    override fun write(buf: PacketByteBuf) {
        buf.writeString(name)
        visualContext.write(buf)
        buf.writeVarInt(variableScopes.size)
        for(scope in variableScopes) {
            buf.writeString(scope.name)
            buf.writeVarInt(scope.variablesReference)
            buf.writeNullableString(scope.presentationHint)
            buf.writeBoolean(scope.isExpensive)
            buf.writeNullableInt(scope.namedVariables)
            buf.writeNullableInt(scope.indexedVariables)
            buf.writeNullableInt(scope.line)
            buf.writeNullableInt(scope.column)
            buf.writeNullableInt(scope.endLine)
            buf.writeNullableInt(scope.endColumn)
        }
        buf.writeEnumConstant(presentationHint)
    }
}