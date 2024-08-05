plugins {
    id("fabric-loom") version System.getProperty("loom_version")
    id("java")
    kotlin("jvm").version(System.getProperty("kotlin_version"))
}

base { archivesName.set(project.extra["archives_base_name"] as String) }
version = project.extra["mod_version"] as String
group = project.extra["maven_group"] as String
repositories {
    maven("https://jitpack.io")
}
dependencies {
    minecraft("com.mojang", "minecraft", project.extra["minecraft_version"] as String)
    mappings("net.fabricmc", "yarn", project.extra["yarn_mappings"] as String, null, "v2")
    modImplementation("net.fabricmc", "fabric-loader", project.extra["loader_version"] as String)
    modImplementation("net.fabricmc.fabric-api", "fabric-api", project.extra["fabric_version"] as String)
    modImplementation("net.fabricmc", "fabric-language-kotlin", project.extra["fabric_language_kotlin_version"] as String)

    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.19.0")
    include("org.eclipse.lsp4j:org.eclipse.lsp4j:0.19.0")
    include("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:0.19.0")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.debug:0.19.0")
    include("org.eclipse.lsp4j:org.eclipse.lsp4j.debug:0.19.0")
    include("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc.debug:0.19.0")

    // Only use within PartialIdGeneratorServiceImpl or other services with the purpose of interacting with PartialIdAutocomplete
    modCompileOnly("com.github.Papierkorb2292:PartialIdAutocomplete:97aac8a") {
        exclude("com.terraformersmc", "modmenu")
    }
}
loom {
    accessWidenerPath.fileValue(file("src/main/resources/command_crafter.accesswidener"))
}
tasks {
    val javaVersion = JavaVersion.toVersion((project.extra["java_version"] as String).toInt())
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        sourceCompatibility = javaVersion.toString()
        targetCompatibility = javaVersion.toString()
        options.release.set(javaVersion.toString().toInt())
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> { kotlinOptions { jvmTarget = javaVersion.toString() } }
    jar { from("LICENSE") { rename { "${it}_${base.archivesName.get()}" } } }
    jar { from("README.md") }
    jar { from("GSON_LICENSE") }
    processResources {
        filesMatching("fabric.mod.json") { expand(mutableMapOf("version" to project.extra["mod_version"] as String, "fabricloader" to project.extra["loader_version"] as String, "fabric_api" to project.extra["fabric_version"] as String, "fabric_language_kotlin" to project.extra["fabric_language_kotlin_version"] as String, "minecraft" to project.extra["minecraft_version"] as String, "java" to project.extra["java_version"] as String)) }
        filesMatching("*.mixins.json") { expand(mutableMapOf("java" to project.extra["java_version"] as String)) }
    }

    java {
        toolchain { languageVersion.set(JavaLanguageVersion.of(javaVersion.toString())) }
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
        withSourcesJar()
    }
}
