package net.papierkorb2292.command_crafter.parser.helper

import net.minecraft.core.HolderLookup

interface HolderLookupProviderContainer {
    fun `command_crafter$getHolderLookups`(): HolderLookup.Provider
}