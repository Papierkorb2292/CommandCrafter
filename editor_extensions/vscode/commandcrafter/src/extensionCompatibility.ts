import * as vscode from 'vscode'
import { LanguageClient } from "vscode-languageclient/node"
import { ConnectionFeature, MinecraftConnectionType } from "./minecraftConnection"
import { FEATURE_CONFIG_DISABLE, FEATURE_CONFIG_ENABLE, getFeatureConfig, isCompatibilityCheckEnabled, SETTINGS_SCOPE, SETTINGS_SECTIONS, updateCurrentFeatureConfig, updateSettingWhereDefined } from './settings'
import { fileExists } from './extension'
import { applyEdits, JSONPath, modify, parse, ParseError } from 'jsonc-parser'
import { outputChannel } from './extensionLog'

const COMPATIBILITY_CHECK_SETTING_NAME = SETTINGS_SCOPE + "." + SETTINGS_SECTIONS.checkExtensionCompatibility
const SPYGLASS_HIGHLIGHTING_SETUP_BUTTONS = {
    ignore: "Ignore",
    disableForSpyglass: "Disable Spyglass highlighting",
    disableForCommandCrafter: "Disable CommandCrafter highlighting",
    openSettings: "Open settings"
}
const SPYGLASS_EXT_ID = "spgoding.datapack-language-server"

export class ExtensionCompatibility implements ConnectionFeature {
    constructor(context: vscode.ExtensionContext) {
        vscode.commands.registerCommand("commandcrafter.openSyntaxHighlightingSettings", () => {
            this.openSyntaxHighlightingSettings()
        })
    }

    onLanguageClientStart(languageClient: LanguageClient): void { }
    onLanguageClientReady(languageClient: LanguageClient): void {
        this.checkSpyglassHighlighting();
    }
    onLanguageClientStop(): void { }
    onConnectionTypeChange(connectionType: MinecraftConnectionType | null): void { }

    // Only checks mcfunction highlighting since that is where conflicts are likely to occur.
    // If CommandCrafter ever provides semantic tokens for mcdoc as well, they likely won't really
    // differ from Spyglass, so the user won't have to pick.
    async checkSpyglassHighlighting() {
        if(vscode.workspace.workspaceFolders === undefined) {
            // No workspace is opened yet, so there are no config files to work with
            return;
        }
        outputChannel?.appendLine("checkSpyglassHighlighting...")
        if(vscode.extensions.getExtension(SPYGLASS_EXT_ID) === undefined) {
            // Spyglass isn't installed/enabled
            outputChannel?.appendLine("Spyglass not installed")
            return;
        }
        if(!isCompatibilityCheckEnabled()) {
            outputChannel?.appendLine("Compatibility check is disabled")
            return;
        }
        if(!isCommandCrafterHighlightingOn()) {
            outputChannel?.appendLine("No conflict detected (CommandCrafter off)")
            return;
        }
        const spyglassHighlightingConfig = await isSpyglassHighlightingOnInConfig()
        const spyglassHighlightingSettings = isSpyglassHighlightingOnInSettings()
        if(spyglassHighlightingSettings === undefined) {
            outputChannel?.appendLine("Check failed due to outdated Spyglass: no semantic coloring setting")
            return;
        }
        if(spyglassHighlightingConfig === false || (spyglassHighlightingConfig === undefined && !spyglassHighlightingSettings)) {
            outputChannel?.appendLine("No conflict detected (Spyglass off)")
            return;
        }

        const promptAnswer = await vscode.window.showInformationMessage(
            "CommandCrafter Setup",
            { modal: true, detail: `It looks like you have highlighting for both Spyglass and CommandCrafter enabled, which can lead to conflicts. Would you like to disable one? (This will edit your settings)

                (You can suppress this message in the settings under ${COMPATIBILITY_CHECK_SETTING_NAME})
            `},
            { title: SPYGLASS_HIGHLIGHTING_SETUP_BUTTONS.ignore, isCloseAffordance: true },
            { title: SPYGLASS_HIGHLIGHTING_SETUP_BUTTONS.disableForSpyglass },
            { title: SPYGLASS_HIGHLIGHTING_SETUP_BUTTONS.openSettings },
            { title: SPYGLASS_HIGHLIGHTING_SETUP_BUTTONS.disableForCommandCrafter },
        );
        this.onSpyglassHighlightingPromptAnswer(spyglassHighlightingConfig, spyglassHighlightingSettings, promptAnswer)
    }

    onSpyglassHighlightingPromptAnswer(spyglassHighlightingConfig: boolean | undefined, spyglassHighlightingSettings: boolean, answer?: { title: string }) {
        switch(answer?.title) {
            case SPYGLASS_HIGHLIGHTING_SETUP_BUTTONS.openSettings:
                this.openSyntaxHighlightingSettings()
                break
            case SPYGLASS_HIGHLIGHTING_SETUP_BUTTONS.disableForCommandCrafter:
                outputChannel?.appendLine("disableCommandCrafterHighlighting...")
                disableCommandCrafterHighlighting()
                break
            case SPYGLASS_HIGHLIGHTING_SETUP_BUTTONS.disableForSpyglass:
                outputChannel?.appendLine("disableSypglassHighlighting...")
                if(spyglassHighlightingConfig)
                    removeSpyglassHighlightingConfig()
                if(spyglassHighlightingSettings)
                    disableSypglassHighlighting()
                break
        }
    }

    openSyntaxHighlightingSettings() {
        vscode.commands.executeCommand('workbench.action.openSettings', `@id:${COMPATIBILITY_CHECK_SETTING_NAME} @id:${SPYGLASS_SETTINGS_SCOPE}.${SPYGLASS_SEMANTIC_COLORING_SETTING_SECTION} @id:${SETTINGS_SCOPE}.${SETTINGS_SECTIONS.featureConfig}`)
    }
}

const MCFUNCTION_HIGHLIGHTING_CONFIG_KEYS = [
    "analyzer.mcfunction.semanticTokens",
    "analyzer.semanticTokens",
];

function isCommandCrafterHighlightingOn(): boolean {
    const featureConfig = getFeatureConfig();
    if(featureConfig === undefined) {
        return true;
    }
    for(const settingKey of MCFUNCTION_HIGHLIGHTING_CONFIG_KEYS) {
        if(featureConfig[settingKey] === FEATURE_CONFIG_ENABLE) {
            return true;
        }
        if(featureConfig[settingKey] === FEATURE_CONFIG_DISABLE) {
            return false;
        }
    }
    return true;
}

function disableCommandCrafterHighlighting() {
    const currentConfig = getFeatureConfig()
    const merged = {
        ...currentConfig,
        [MCFUNCTION_HIGHLIGHTING_CONFIG_KEYS[0]]: FEATURE_CONFIG_DISABLE
    }
    updateCurrentFeatureConfig(merged)
    notifySettingChanged(`${SETTINGS_SCOPE}.${SETTINGS_SECTIONS.featureConfig}`)
}

const SPYGLASS_CONFIG_FILES = ['spyglass.json', '.spyglassrc', '.spyglassrc.json']
const SPYGLASS_SETTINGS_SCOPE = "spyglassmc"
const SPYGLASS_SEMANTIC_COLORING_SETTING_SECTION = "env.feature.semanticColoring.disabledLanguages"

async function isSpyglassHighlightingOnInConfig(): Promise<boolean | undefined> {
    // Spyglass technically prioritizes configs from later workspace folders, so go through them in reverse order

    const projectRoots = vscode.workspace.workspaceFolders!!
    for(let i = projectRoots.length - 1; i >= 0; i--) {
        const projectRoot = projectRoots[i].uri
        // Go through names in normal order, because Spyglass only uses the first one it can parse
        for (const name of SPYGLASS_CONFIG_FILES) {
            const uri = vscode.Uri.joinPath(projectRoot, name)
            if(await fileExists(uri)) {
                try {
                    const content = await vscode.workspace.fs.readFile(uri)
                    const config = JSON.parse(Buffer.from(content).toString())
                    const semanticColoringOption = config.env?.feature?.semanticColoring
                    if(semanticColoringOption !== undefined) {
                        return semanticColoringOption
                    }
                } catch(err) {
                    outputChannel?.appendLine(`Error reading Spyglass config at ${uri}: ${err}`)
                }
                break
            }
        }
    }

    return undefined
}

function isSpyglassHighlightingOnInSettings(): boolean | undefined {
    if(!vscode.workspace.getConfiguration(SPYGLASS_SETTINGS_SCOPE).has(SPYGLASS_SEMANTIC_COLORING_SETTING_SECTION))
        return undefined
    const disabledLanguages = vscode.workspace.getConfiguration(SPYGLASS_SETTINGS_SCOPE).get(SPYGLASS_SEMANTIC_COLORING_SETTING_SECTION)
    return !Array.isArray(disabledLanguages) || !disabledLanguages.includes("mcfunction")
}

const SPYGLASS_SEMANTIC_COLORING_PATH: JSONPath = ["env", "feature", "semanticColoring"]

function disableSypglassHighlighting() {
    let disabledLanguages = vscode.workspace.getConfiguration(SPYGLASS_SETTINGS_SCOPE).get(SPYGLASS_SEMANTIC_COLORING_SETTING_SECTION)
    if(!Array.isArray(disabledLanguages)) {
        disabledLanguages = []
    }
    (disabledLanguages as any[]).push('mcfunction')
    updateSettingWhereDefined(SPYGLASS_SETTINGS_SCOPE, SPYGLASS_SEMANTIC_COLORING_SETTING_SECTION, disabledLanguages)
    notifySettingChanged(`${SPYGLASS_SETTINGS_SCOPE}.${SPYGLASS_SEMANTIC_COLORING_SETTING_SECTION}`)
}

async function removeSpyglassHighlightingConfig() {
    // Spyglass wants the `env` value in all workspaces to be the same, so remove it everywhere
    for(const workspaceFolder of vscode.workspace.workspaceFolders!!) {
        for (const name of SPYGLASS_CONFIG_FILES) {
            const uri = vscode.Uri.joinPath(workspaceFolder.uri, name)
            if(await fileExists(uri)) {
                try {
                    const content = await vscode.workspace.fs.readFile(uri) 
                    let config = Buffer.from(content).toString()
                    const parseErrors: ParseError[] = []
                    parse(config, parseErrors)
                    if(parseErrors.length !== 0) {
                        outputChannel?.appendLine(`Errors parsing Spyglass config at ${uri}: ${parseErrors}`)
                        continue
                    }

                    config = applyEdits(config, modify(config, SPYGLASS_SEMANTIC_COLORING_PATH, undefined, {}))
                    const newContent = Uint8Array.from(Buffer.from(config))
                    await vscode.workspace.fs.writeFile(uri, newContent)
                } catch(err) {
                    outputChannel?.appendLine(`Error modifying Spyglass config at ${uri}: ${err}`)
                }
            }
        }
    }
}

function notifySettingChanged(name: string) {
    vscode.window.showInformationMessage(
        "CommandCrafter Setup",
        { modal: true, detail: `The setting '${name}' has been updated` }
    )
}