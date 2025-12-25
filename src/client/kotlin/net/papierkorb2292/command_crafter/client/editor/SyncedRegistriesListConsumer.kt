package net.papierkorb2292.command_crafter.client.editor

import net.minecraft.resources.RegistryDataLoader

interface SyncedRegistriesListConsumer {
    fun `command_crafter$setSyncedRegistriesList`(syncedRegistriesList: List<RegistryDataLoader.RegistryData<*>>)
}