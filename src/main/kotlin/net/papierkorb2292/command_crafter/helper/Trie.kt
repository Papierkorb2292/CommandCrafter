package net.papierkorb2292.command_crafter.helper

/**
 * https://en.wikipedia.org/wiki/Trie<br/>
 * Maps sequences of [Key]s to one [Value] for each sequence using a trie structure
 */
class Trie<Key, Value>(private val childMapCapacity: Int = 16) {
    private val root: Node<Key, Value> = Node(null, childMapCapacity)

    fun put(keySequence: () -> Key?, value: Value): Boolean {
        var currentKey = keySequence()
        var currentNode = root
        while(currentKey != null) {
            currentNode = currentNode.children.getOrPut(currentKey) { Node(null, childMapCapacity) }
            currentKey = keySequence()
        }
        if(currentNode.value != value) {
            currentNode.value = value
            return true
        }
        return false
    }

    fun remove(keySequence: () -> Key?): Boolean {
        var currentKey = keySequence()
        var currentNode = root
        var branchToRemove: Pair<Node<Key, Value>, Key>? = null
        while(currentKey != null) {
            branchToRemove = if(currentNode.children.size == 1 && branchToRemove == null)
                currentNode to currentKey
            else
                null
            val nextNode = currentNode.children[currentKey] ?: break
            currentNode = nextNode
            currentKey = keySequence()
        }
        branchToRemove?.run {
            first.children.remove(second)
            return true
        }
        return false
    }

    fun get(keySequence: () -> Key?): Value? {
        var currentKey = keySequence()
        var currentNode = root
        while(currentKey != null) {
            currentNode = currentNode.children[currentKey] ?: return null
            currentKey = keySequence()
        }
        return currentNode.value
    }

    fun clear() = root.children.clear()

    private class Node<Key, Value>(var value: Value?, childMapCapacity: Int) {
        val children: MutableMap<Key, Node<Key, Value>> = HashMap(childMapCapacity)
    }
}