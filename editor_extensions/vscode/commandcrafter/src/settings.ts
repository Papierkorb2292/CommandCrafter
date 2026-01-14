import * as vscode from 'vscode';
export const SETTINGS_SCOPE = "CommandCrafter"
export const SETTINGS_SECTIONS = {
    minecraftAddress: "MinecraftAddress",
    featureConfig: "FeatureConfig",
    checkExtensionCompatibility: "CheckExtensionCompatibility"
}

export function getMinecraftAddress(): string | undefined {
    return vscode.workspace.getConfiguration(SETTINGS_SCOPE).get<string>(SETTINGS_SECTIONS.minecraftAddress)
}

export function isCompatibilityCheckEnabled(): boolean {
    return vscode.workspace.getConfiguration(SETTINGS_SCOPE).get<boolean>(SETTINGS_SECTIONS.checkExtensionCompatibility, true)
}

export function getFeatureConfig(): FeatureConfig {
    return vscode.workspace.getConfiguration(SETTINGS_SCOPE).get<FeatureConfig>(SETTINGS_SECTIONS.featureConfig, {})
}

export function getLocalFeatureConfig(): FeatureConfig {
    const configInspect = vscode.workspace.getConfiguration(SETTINGS_SCOPE).inspect<FeatureConfig>(SETTINGS_SECTIONS.featureConfig)
    return configInspect?.workspaceValue ?? {}
}

export function getGlobalFeatureConfig(): FeatureConfig {
    const configInspect = vscode.workspace.getConfiguration(SETTINGS_SCOPE).inspect<FeatureConfig>(SETTINGS_SECTIONS.featureConfig)
    return configInspect?.globalValue ?? {}
}

export function insertDefaultFeatureConfig(defaultConfig: FeatureConfig) {
    const previous = getGlobalFeatureConfig()
    const merged = Object.assign(defaultConfig, previous)
    vscode.workspace.getConfiguration(SETTINGS_SCOPE)
        .update(SETTINGS_SECTIONS.featureConfig, merged, vscode.ConfigurationTarget.Global)
}

export function updateCurrentFeatureConfig(newConfig: FeatureConfig) {
    updateSettingWhereDefined(SETTINGS_SCOPE, SETTINGS_SECTIONS.featureConfig, newConfig)
}

export function updateSettingWhereDefined(scope: string, section: string, value: any) {
    const configInspect = vscode.workspace.getConfiguration(scope).inspect(section)
    const configurationTarget = configInspect?.workspaceValue ? vscode.ConfigurationTarget.Workspace : vscode.ConfigurationTarget.Global
    vscode.workspace.getConfiguration(scope).update(section, value, configurationTarget)
}

export type FeatureConfig = { [key: string]: string }

export const FEATURE_CONFIG_ENABLE = "enable"
export const FEATURE_CONFIG_DISABLE = "disable"