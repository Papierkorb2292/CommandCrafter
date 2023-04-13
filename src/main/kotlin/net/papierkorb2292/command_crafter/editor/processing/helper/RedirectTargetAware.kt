package net.papierkorb2292.command_crafter.editor.processing.helper

interface RedirectTargetAware {
    fun `command_crafter$setIsRedirectTarget`(isRedirectTarget: Boolean)
    fun `command_crafter$isRedirectTarget`(): Boolean
}