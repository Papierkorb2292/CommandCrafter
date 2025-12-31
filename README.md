# CommandCrafter

The CommandCrafter Minecraft mod aims to provide useful tools for command development.

With this mod and the corresponding [CommandCrafter vscode extension](https://marketplace.visualstudio.com/items?itemName=Papierkorb2292.commandcrafter), it is possible to connect vscode to the running Minecraft instance and get datapack language support for whatever Minecraft you're running. Besides syntax highlighting, error checking, definition providers and hover providers, this includes debugging functionality that allows you to set breakpoints and step through functions while keeping track of the current command context. Additionally, vscode can show you the log output of the server you're on and lets you directly execute commands from the vscode window. Furthermore, this project lets you view scoreboards and storages in VSCode as well as reload datapacks or resourcepacks using the corresponding VSCode commands (which can also be bound to keyboard shortcuts).

Additionally, the mod includes a flexible extension of the vanilla datapack parser, which makes new function syntax possible. This mod provides an improved version of the vanilla function language, currently offering multiline commands without backslashes and inline functions/tags. This new function syntax can be transpiled to a vanilla datapack by the mod using the `/datapack build` command.

For more info, visit the [wiki](https://github.com/Papierkorb2292/CommandCrafter/wiki).

The following is an example of the changes this extension can
make in vscode:
![A screenshot of vscode](editor_extensions/vscode/ExampleImage.png)

## Installation and Usage

For information on how to install and use the mod, visit the [Getting Started](https://github.com/Papierkorb2292/CommandCrafter/wiki/Getting-Started) page on the wiki.

## License

This mod is licensed under the MIT License (see [LICENSE](LICENSE)).

This mod distributes with the [LSP4J library](https://github.com/eclipse-lsp4j/lsp4j), which is licensed under the Eclipse Distribution License 1.0 (see https://www.eclipse.org/org/documents/edl-v10.html), and distributes with parts of the [GSON library](https://github.com/google/gson), which is licensed under the Apache License 2.0 (see https://github.com/google/gson/blob/main/gson/LICENSE).  

# Special Thanks
I want to thank all the following awesome people for testing and giving feedback for this project:
- [Kesuaheli](https://www.github.com/Kesuaheli)
- [Percy](https://www.youtube.com/@percyarin.studios)
- [Mantis Whale](https://github.com/JabraelNoael)
- Energy
- [Yondonator](https://www.github.com/yondonator)
- [Mqxx](https://www.github.com/Mqxx)