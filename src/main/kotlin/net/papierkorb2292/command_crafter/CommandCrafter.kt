package net.papierkorb2292.command_crafter
import net.fabricmc.api.ModInitializer
import org.apache.logging.log4j.LogManager

class CommandCrafter: ModInitializer {
    private val modId = "command_crafter"
    private val logger = LogManager.getLogger(modId)
    override fun onInitialize() {
        logger.info("Loaded CommandCrafter!")
    }
}