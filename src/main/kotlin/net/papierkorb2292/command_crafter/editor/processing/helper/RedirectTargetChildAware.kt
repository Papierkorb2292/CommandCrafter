package net.papierkorb2292.command_crafter.editor.processing.helper

interface RedirectTargetChildAware {
    fun `command_crafter$setIsRedirectTargetChild`(isRedirectTargetChild: Boolean)
    fun `command_crafter$isRedirectTargetChild`(): Boolean
}