package net.papierkorb2292.command_crafter.config

import net.papierkorb2292.command_crafter.CommandCrafter
import java.io.IOException
import java.nio.file.Path
import java.util.*
import kotlin.io.path.notExists
import kotlin.io.path.reader
import kotlin.io.path.writer

class CommandCrafterConfig private constructor(
    servicesPort: Int,
    val configPath: Path
) {
    var servicesPort = servicesPort
        set(value) {
            field = value
            servicesPortChangedListeners.forEach { it(value) }
        }

    private val servicesPortChangedListeners = mutableListOf<(Int) -> Unit>()

    companion object {
        val DEFAULT_CONFIG_PATH = Path.of("config/command_crafter.properties")

        val SERVICES_PORT_NAME = "services-port"
        private val DEFAULT_SERVICES_PORT = 52853

        private val CONFIG_DEFAULTS = Properties().apply {
            setProperty(SERVICES_PORT_NAME, DEFAULT_SERVICES_PORT.toString())
        }

        fun fromProperties(properties: Properties, configPath: Path, configVersion: String): CommandCrafterConfig {
            var servicesPort = properties.getProperty(SERVICES_PORT_NAME).toIntOrNull()
            if(servicesPort == null) {
                servicesPort = DEFAULT_SERVICES_PORT
                CommandCrafter.LOGGER.warn("Encountered invalid services port in config file, using default value $servicesPort")
            }
            return CommandCrafterConfig(servicesPort, configPath)
        }

        fun fromFile(configPath: Path): CommandCrafterConfig {
            val properties = Properties(CONFIG_DEFAULTS)
            if(configPath.notExists()) {
                val config = CommandCrafterConfig(DEFAULT_SERVICES_PORT, configPath)
                config.saveToFile()
                return config
            }
            try {
                val reader = configPath.reader().buffered()
                reader.mark(16)
                val firstChar = reader.read()
                val configVersion = if(firstChar == 'v'.code) {
                    reader.readLine()
                } else {
                    reader.reset()
                    CommandCrafter.VERSION
                }
                properties.load(reader)
                return fromProperties(properties, configPath, configVersion)
            } catch(e: IOException) {
                CommandCrafter.LOGGER.error("Failed to load config file", e)
                return fromProperties(Properties(CONFIG_DEFAULTS), configPath, CommandCrafter.VERSION)
            }
        }
    }

    fun saveToFile() {
        try {
            configPath.parent?.toFile()?.mkdirs()
            val writer = configPath.writer()
            writer.append("v${CommandCrafter.VERSION}\n")
            val properties = Properties()
            properties.setProperty(SERVICES_PORT_NAME, servicesPort.toString())
            properties.store(writer, "CommandCrafter Config")
        } catch(e: IOException) {
            CommandCrafter.LOGGER.error("Failed to save config file", e)
        }
    }

    fun addServicesPortChangedListener(listener: (Int) -> Unit) {
        servicesPortChangedListeners += listener
    }
}