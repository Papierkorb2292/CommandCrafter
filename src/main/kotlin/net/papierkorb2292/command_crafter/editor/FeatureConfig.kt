package net.papierkorb2292.command_crafter.editor

class FeatureConfig(private val entries: Map<String, Entry>) {
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

    enum class Entry {
        ENABLE,
        DISABLE
    }
}