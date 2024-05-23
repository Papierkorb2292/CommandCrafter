package net.papierkorb2292.command_crafter.editor.debugger.server

class StepInTargetsManager {
    private val stepInTargets = mutableListOf<Target>()

    fun addStepInTarget(target: Target): Int {
        stepInTargets.add(target)
        return stepInTargets.size - 1
    }

    fun stepIn(targetId: Int): Boolean {
        if(targetId >= stepInTargets.size)
            return false
        stepInTargets[targetId].stepInCallback()
        return true
    }

    fun clear()
        = stepInTargets.clear()

    class Target(val stepInCallback: () -> Unit)
}