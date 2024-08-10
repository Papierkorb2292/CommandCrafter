package net.papierkorb2292.command_crafter.client

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import net.papierkorb2292.command_crafter.CommandCrafter

object CommandCrafterModMenu : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<CommandCrafterConfigScreen> = ConfigScreenFactory  { parent ->
        CommandCrafterConfigScreen(CommandCrafter.config, parent)
    }
}