package net.papierkorb2292.command_crafter.client.helper

import net.minecraft.registry.RegistryLoader

interface SyncedRegistriesListConsumer {
    fun `command_crafter$setSyncedRegistriesList`(syncedRegistriesList: List<RegistryLoader.Entry<*>>)
}