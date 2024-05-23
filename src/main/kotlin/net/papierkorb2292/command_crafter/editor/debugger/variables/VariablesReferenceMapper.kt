package net.papierkorb2292.command_crafter.editor.debugger.variables

interface VariablesReferenceMapper : VariablesReferencer {
    /**
     * Registers a variable referencer that can be accessed by methods
     * inherited from [VariablesReferencer]. Returns the id of the
     * variable reference, that can be sent to the editor and used to
     * access the registered variable referencer.
     * It is important that the returned id is a positive number.
     */
    fun addVariablesReferencer(referencer: VariablesReferencer): Int
}