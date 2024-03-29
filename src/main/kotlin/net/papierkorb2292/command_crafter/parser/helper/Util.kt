package net.papierkorb2292.command_crafter.parser.helper

import com.mojang.brigadier.tree.CommandNode

fun CommandNode<*>.visitChildrenRecursively(visitor: (CommandNode<*>) -> Unit) {
    visitor(this)
    for(child in children) {
        child.visitChildrenRecursively(visitor)
    }
}