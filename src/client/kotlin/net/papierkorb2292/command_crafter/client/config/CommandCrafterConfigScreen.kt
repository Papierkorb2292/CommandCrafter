package net.papierkorb2292.command_crafter.client.config

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.option.GameOptionsScreen
import net.minecraft.client.gui.tooltip.Tooltip
import net.minecraft.client.option.GameOptions
import net.minecraft.client.option.SimpleOption
import net.minecraft.text.Text
import net.papierkorb2292.command_crafter.config.CommandCrafterConfig

class CommandCrafterConfigScreen(val config: CommandCrafterConfig, parent: Screen, gameOptions: GameOptions, title: Text) : GameOptionsScreen(parent, gameOptions, title) {

    constructor(
        config: CommandCrafterConfig,
        parent: Screen,
    ) : this(
        config,
        parent,
        MinecraftClient.getInstance().options,
        Text.translatable("command_crafter.config.title")
    )

    override fun addOptions() {
        body!!.addSingleOptionEntry(
            SimpleOption(
                configNameToOptionKey(CommandCrafterConfig.SERVICES_PORT_NAME),
                { Tooltip.of(Text.translatable(configNameToOptionKey(CommandCrafterConfig.SERVICES_PORT_NAME) + ".description")) },
                { _, value -> Text.literal(value.toString())},
                SimpleOptionIntCallbacks,
                config.servicesPort,
                config::servicesPort::set
            )
        )
    }

    private fun configNameToOptionKey(configName: String): String
        = "command_crafter.config." + configName.replace('-', '_')
}