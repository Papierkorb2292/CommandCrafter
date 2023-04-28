package net.papierkorb2292.command_crafter.editor.processing

import org.eclipse.lsp4j.Position
import java.util.*

class AnalyzingResourceCreator() {
    val functionStack: Deque<Position> = LinkedList()
    init {
        functionStack.push(Position(0, 0))
    }

    constructor(resourceCreator: AnalyzingResourceCreator): this() {
        functionStack.clear()
        functionStack.addAll(resourceCreator.functionStack)
    }
}