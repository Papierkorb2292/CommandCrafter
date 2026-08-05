package net.papierkorb2292.command_crafter.editor

import com.mojang.serialization.Codec
import net.minecraft.util.StringRepresentable

class FeatureConfig(private val entries: Map<String, Entry>) {
    companion object {
        val EMPTY = FeatureConfig(emptyMap())
        val DEFAULT_ENTRIES = mapOf(
            "analyzer.completions" to Entry.ENABLE,
            "analyzer.hovers" to Entry.ENABLE,
            "analyzer.definitions" to Entry.ENABLE,
            "analyzer.diagnostics" to Entry.ENABLE,
            "analyzer.semanticTokens" to Entry.ENABLE,
            "analyzer.color" to Entry.ENABLE,
            MinecraftLanguageServer.AUTO_RELOAD_DATAPACK_FUNCTIONS_CONFIG_PATH to Entry.DISABLE,
            MinecraftLanguageServer.AUTO_RELOAD_DATAPACK_JSON_CONFIG_PATH to Entry.DISABLE,
            MinecraftLanguageServer.AUTO_RELOAD_RESOURCEPACK_CONFIG_PATH to Entry.DISABLE
        )

        val ENTRY_CODEC = StringRepresentable.fromEnum(Entry::values)
        val CODEC = Codec.unboundedMap(Codec.STRING, ENTRY_CODEC)
            .xmap(::FeatureConfig, FeatureConfig::entries)
    }

    /**
     * Returns whether a feature is enabled or disabled by the config.
     * If the config doesn't have a corresponding entry, the default value is returned.
     */
    fun isEnabled(feature: String, default: Boolean) = entries[feature]?.equals(Entry.ENABLE) ?: default

    /**
     * Returns whether a feature is enabled or disabled by the config.
     * The first feature name in the list that is found in the config is used.
     * If the config doesn't have a corresponding entry for any feature name, the default value is returned.
     */
    fun isEnabled(feature: List<String>, default: Boolean): Boolean {
        for (featureName in feature) {
            val entry = entries[featureName]
            if(entry != null)
                return entry == Entry.ENABLE
        }
        return default
    }

    enum class Entry : StringRepresentable {
        ENABLE,
        DISABLE;

        override fun getSerializedName() = name.lowercase()
    }
}