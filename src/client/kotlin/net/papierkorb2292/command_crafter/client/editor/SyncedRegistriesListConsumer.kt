package net.papierkorb2292.command_crafter.client.editor

import net.minecraft.registry.RegistryLoader

interface SyncedRegistriesListConsumer {
    fun `command_crafter$setSyncedRegistriesList`(syncedRegistriesList: List<RegistryLoader.Entry<*>>)
}