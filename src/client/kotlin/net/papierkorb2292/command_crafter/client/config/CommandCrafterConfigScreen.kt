package net.papierkorb2292.command_crafter.client.config

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.options.OptionsSubScreen
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.Options
import net.minecraft.client.OptionInstance
import net.minecraft.network.chat.Component
import net.papierkorb2292.command_crafter.config.CommandCrafterConfig

class CommandCrafterConfigScreen(val config: CommandCrafterConfig, parent: Screen, gameOptions: Options, title: Component) : OptionsSubScreen(parent, gameOptions, title) {

    constructor(
        config: CommandCrafterConfig,
        parent: Screen,
    ) : this(
        config,
        parent,
        Minecraft.getInstance().options,
        Component.translatable("command_crafter.config.title")
    )

    override fun addOptions() {
        list!!.addBig(
            OptionInstance(
                configNameToOptionKey(CommandCrafterConfig.SERVICES_PORT_NAME),
                { Tooltip.create(Component.translatable(configNameToOptionKey(CommandCrafterConfig.SERVICES_PORT_NAME) + ".description")) },
                { _, value -> Component.literal(value.toString())},
                SimpleOptionIntCallbacks,
                config.servicesPort,
                config::servicesPort::set
            )
        )
    }

    private fun configNameToOptionKey(configName: String): String
        = "command_crafter.config." + configName.replace('-', '_')
}