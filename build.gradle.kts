import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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
    maven {
        name = "Terraformers"
        setUrl("https://maven.terraformersmc.com/")
    }
}
dependencies {
    minecraft("com.mojang", "minecraft", project.extra["minecraft_version"] as String)
    mappings("net.fabricmc", "yarn", project.extra["yarn_mappings"] as String, null, "v2")
    modImplementation("net.fabricmc", "fabric-loader", project.extra["loader_version"] as String)
    modImplementation("net.fabricmc.fabric-api", "fabric-api", project.extra["fabric_version"] as String)
    modImplementation("net.fabricmc", "fabric-language-kotlin", project.extra["fabric_language_kotlin_version"] as String)

    modCompileOnly("com.terraformersmc:modmenu:${project.extra["modmenu_version"]}")

    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.19.0")
    include("org.eclipse.lsp4j:org.eclipse.lsp4j:0.19.0")
    include("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:0.19.0")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.debug:0.19.0")
    include("org.eclipse.lsp4j:org.eclipse.lsp4j.debug:0.19.0")
    include("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc.debug:0.19.0")

    // Only use within PartialIdGeneratorServiceImpl or other services with the purpose of interacting with PartialIdAutocomplete
    modCompileOnly("com.github.Papierkorb2292:PartialIdAutocomplete:1.2.0") {
        exclude("com.terraformersmc", "modmenu")
    }

    // Only for tests in dev environment
    implementation("io.github.java-diff-utils:java-diff-utils:4.12")
}
loom {
    accessWidenerPath.fileValue(file("src/main/resources/command_crafter.accesswidener"))
    splitEnvironmentSourceSets()
    mods {
        create("commandcrafter") {
            sourceSet("main")
            sourceSet("client")
        }
    }

    runs {
        create("gametest") {
            server()
            ideConfigGenerated(true)
            source(sourceSets["main"])
            name("Game Test")
            vmArg("-Dfabric-api.gametest")
            vmArg("-Dfabric-api.gametest.report-file=${project.layout.buildDirectory}/junit.xml")
            runDir("build/gametest")
        }
    }
}
tasks {
    val javaVersion = JavaVersion.toVersion((project.extra["java_version"] as String).toInt())
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        sourceCompatibility = javaVersion.toString()
        targetCompatibility = javaVersion.toString()
        options.release.set(javaVersion.toString().toInt())
    }
    withType<KotlinCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
        }
    }
    jar { from("LICENSE") { rename { "${it}_${base.archivesName.get()}" } } }
    jar { from("README.md") }
    jar { from("GSON_LICENSE") }
    jar { from("LSP4J_LICENSE") }
    processResources {
        filesMatching("fabric.mod.json") {
            expand("version" to project.extra["mod_version"] as String)
        }
    }

    java {
        toolchain { languageVersion.set(JavaLanguageVersion.of(javaVersion.toString())) }
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
        withSourcesJar()
    }
}