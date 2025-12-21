import * as vscode from 'vscode'
import { LanguageClient } from "vscode-languageclient/node"
import { ConnectionFeature, MinecraftConnectionType } from "./minecraftConnection"
import { FEATURE_CONFIG_DISABLE, FEATURE_CONFIG_ENABLE, getFeatureConfig, getLocalFeatureConfig, isCompatibilityCheckEnabled, SETTINGS_SCOPE, SETTINGS_SECTIONS, updateLocalFeatureConfig } from './settings'
import { fileExists } from './extension'
import { applyEdits, JSONPath, modify } from 'jsonc-parser'

const COMPATIBILITY_CHECK_SETTING_NAME = SETTINGS_SCOPE + "." + SETTINGS_SECTIONS.checkExtensionCompatiblity
const SPYGLASS_HIGHLIGHTING_SETUP_BUTTONS = {
    ignore: "Ignore",
    disableForSpyglass: "Disable Spyglass highlighting",
    disableForCommandCrafter: "Disable CommandCrafter highlighting",
    openSettings: "Open settings"
}
const SPYGLASS_EXT_ID = "spgoding.datapack-language-server"

export class ExtensionCompatibility implements ConnectionFeature {
    onLanguageClientStart(languageClient: LanguageClient): void { }
    onLanguageClientReady(languageClient: LanguageClient): void {
        // TODO: Logging
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
        if(vscode.extensions.getExtension(SPYGLASS_EXT_ID) === undefined) {
            // Spyglass isn't installed/enabled
            return;
        }
        if(!isCompatibilityCheckEnabled() || !isCommandCrafterHighlightingOn() || !await isSpyglassHighlightingOn()) {
            return;
        }

        const promptAnswer = await vscode.window.showInformationMessage(
            "CommandCrafter Setup",
            { modal: true, detail: `It looks like you have highlighting for both Spyglass and CommandCrafter enabled for this project, which can lead to conflicts. Would you like to disable one? (This will edit config files in your workspace)

                (You can suppress this message in the settings under ${COMPATIBILITY_CHECK_SETTING_NAME})
            `},
            { title: SPYGLASS_HIGHLIGHTING_SETUP_BUTTONS.ignore, isCloseAffordance: true },
            { title: SPYGLASS_HIGHLIGHTING_SETUP_BUTTONS.disableForSpyglass },
            { title: SPYGLASS_HIGHLIGHTING_SETUP_BUTTONS.openSettings },
            { title: SPYGLASS_HIGHLIGHTING_SETUP_BUTTONS.disableForCommandCrafter },
        );
        this.onSpyglassHighlightingPromptAnswer(promptAnswer)
    }

    onSpyglassHighlightingPromptAnswer(answer?: { title: string }) {
        switch(answer?.title) {
            case SPYGLASS_HIGHLIGHTING_SETUP_BUTTONS.openSettings:
                vscode.commands.executeCommand("vscode.open", vscode.Uri.parse(`vscode://settings/${COMPATIBILITY_CHECK_SETTING_NAME}`))
                break
            case SPYGLASS_HIGHLIGHTING_SETUP_BUTTONS.disableForCommandCrafter:
                disableCommandCrafterHighlighting()
                break
            case SPYGLASS_HIGHLIGHTING_SETUP_BUTTONS.disableForSpyglass:
                disableSpyglassHighlighting()
                break
        }
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
    const localConfig = getLocalFeatureConfig()
    const merged = {
        ...localConfig,
        [MCFUNCTION_HIGHLIGHTING_CONFIG_KEYS[0]]: FEATURE_CONFIG_DISABLE
    }
    updateLocalFeatureConfig(merged)
}

const SPYGLASS_CONFIG_FILES = ['spyglass.json', '.spyglassrc', '.spyglassrc.json']

async function isSpyglassHighlightingOn(): Promise<boolean> {
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
                } catch(ignored) { }
                break
            }
        }
    }

    return true;
}

const SPYGLASS_SEMANTIC_COLORING_PATH: JSONPath = ["env", "feature", "semanticColoring"]

//TODO: Only disable highlighting for functions, not mcdoc
async function disableSpyglassHighlighting() {
    let foundFile = false
    // Spyglass wants the `env` value in all workspaces to be the same, so set it everywhere
    for(const workspaceFolder of vscode.workspace.workspaceFolders!!) {
        for (const name of SPYGLASS_CONFIG_FILES) {
            const uri = vscode.Uri.joinPath(workspaceFolder.uri, name)
            if(await fileExists(uri)) {
                try {
                    const content = await vscode.workspace.fs.readFile(uri) 
                    const config = Buffer.from(content).toString()
                    const edits = modify(config, SPYGLASS_SEMANTIC_COLORING_PATH, false, {});
                    // TODO: Also add a comment to jsonc files (once Spyglass supports them) that the value was updated by CommandCrafter
                    const newConfig = applyEdits(config, edits)
                    const newContent = Uint8Array.from(Buffer.from(newConfig))
                    await vscode.workspace.fs.writeFile(uri, newContent)
                    foundFile = true
                } catch(ignored) { }
            }
        }
    }

    if(!foundFile) {
        // Create new config file
        const uri = vscode.Uri.joinPath(vscode.workspace.workspaceFolders!![0].uri, SPYGLASS_CONFIG_FILES[0])
        const newConfig = JSON.stringify({
            env: {
                feature: {
                    semanticColoring: false
                }
            }
        }, null, 4)
        const newContent = Uint8Array.from(Buffer.from(newConfig))
        await vscode.workspace.fs.writeFile(uri, newContent)
    }
}