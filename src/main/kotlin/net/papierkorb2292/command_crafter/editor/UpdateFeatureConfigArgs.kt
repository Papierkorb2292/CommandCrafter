package net.papierkorb2292.command_crafter.editor

class UpdateFeatureConfigArgs(
    // This has to be a HashMap, because otherwise GSON wouldn't recognize the FeatureConfig.Entry type adapter
    // (which is set separately to support lowercase strings)
    var featureConfig: HashMap<String, FeatureConfig.Entry>?
) {
    constructor() : this(null)

    fun combineWithEditorInfo(editorInfo: EditorConnectionManager.EditorInfo): EditorConnectionManager.EditorInfo {
        val newFeatureConfig = featureConfig ?: return editorInfo
        return EditorConnectionManager.EditorInfo(
            FeatureConfig(newFeatureConfig),
            editorInfo.extensionVersion
        )
    }
}