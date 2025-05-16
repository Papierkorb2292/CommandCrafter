package net.papierkorb2292.command_crafter.parser.helper

import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.RootCommandNode
import net.minecraft.command.CommandSource
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.papierkorb2292.command_crafter.helper.runWithValue
import net.papierkorb2292.command_crafter.mixin.editor.CommandManagerAccessor

fun CommandNode<*>.visitChildrenRecursively(visitor: (CommandNode<*>) -> Unit) {
    visitor(this)
    for(child in children) {
        child.visitChildrenRecursively(visitor)
    }
}

val IS_BUILDING_CLIENTSIDE_COMMAND_TREE = ThreadLocal<Boolean>()

fun limitCommandTreeForSource(commandManager: CommandManager, source: ServerCommandSource): RootCommandNode<CommandSource> {
    val rootNode = RootCommandNode<ServerCommandSource>()
    val newCommandTreeMapping = mutableMapOf<CommandNode<ServerCommandSource>, CommandNode<ServerCommandSource>>(commandManager.dispatcher.root to rootNode)
    IS_BUILDING_CLIENTSIDE_COMMAND_TREE.runWithValue(true) {
        CommandManagerAccessor.callMakeTreeForSource(
            commandManager.dispatcher.root,
            rootNode,
            source,
            newCommandTreeMapping
        )
    }
    @Suppress("UNCHECKED_CAST")
    return rootNode as RootCommandNode<CommandSource>
}

fun <S> CommandNode<S>.resolveRedirects(): CommandNode<S> {
    var node = this
    while(node.redirect != null)
        node = node.redirect
    return node
}