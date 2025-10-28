import com.google.gson.Gson
import com.google.gson.JsonObject
import org.apache.log4j.LogManager
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import javax.net.ssl.HttpsURLConnection

// Inspired by https://gitlab.com/supersaiyansubtlety-group/minecraft-mods/sss_mod_gradle/-/blob/18a6c4c0fa75603fe7ba0e508439a55381ead45e/plugin/src/main/java/net/sssubtlety/sss_mod_gradle/plugin/RunConfigHelperPlugin.java
val mixinJavaagentArgFile = file(".gradle/mixin-javaagent-arg.txt")

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

    api("org.eclipse.lsp4j:org.eclipse.lsp4j:0.19.0")
    include("org.eclipse.lsp4j:org.eclipse.lsp4j:0.19.0")
    include("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:0.19.0")
    include("org.eclipse.xtext:org.eclipse.xtext.xbase.lib:2.28.0")
    api("org.eclipse.lsp4j:org.eclipse.lsp4j.debug:0.19.0")
    include("org.eclipse.lsp4j:org.eclipse.lsp4j.debug:0.19.0")
    include("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc.debug:0.19.0")

    // Only use within PartialIdGeneratorServiceImpl or other services with the purpose of interacting with PartialIdAutocomplete
    modCompileOnly("com.github.Papierkorb2292:PartialIdAutocomplete:1.2.0") {
        exclude("com.terraformersmc", "modmenu")
    }

    // Only for tests in dev environment
    implementation("io.github.java-diff-utils:java-diff-utils:4.12")
    implementation("com.fasterxml.jackson.core:jackson-core:2.20.0")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.20")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.0")
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

        getByName("client") {
            vmArg("@$mixinJavaagentArgFile")
            val devUsername = project.extra["dev_username"] as String
            programArg("--username=$devUsername")
            val uuid = fetchPlayerUUID(devUsername)
            if(uuid != null)
                programArg("--uuid=$uuid")
        }

        getByName("server") {
            vmArg("@$mixinJavaagentArgFile")
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

    val createMixinJavaArgFileTask = register("createMixinJavaAgentArgFile") {
        val compileClasspathFiles = files(configurations.named("compileClasspath"))
        val mixinLibraryFile = compileClasspathFiles.filter { file ->
            file.toString().contains("sponge-mixin")
        }.singleFile
        Files.write(Path.of(mixinJavaagentArgFile.absolutePath), listOf("-javaagent:${mixinLibraryFile.absolutePath}"))
    }
    getByName("ideaSyncTask").dependsOn(createMixinJavaArgFileTask)
    getByName("processResources").dependsOn(createMixinJavaArgFileTask)
}

fun fetchPlayerUUID(playerName: String): UUID? {
    val logger = LogManager.getLogger("PlayerFetcher")
    try {
        val url = URI("https://api.mojang.com/users/profiles/minecraft/$playerName").toURL()
        val con = url.openConnection() as HttpsURLConnection
        con.requestMethod = "GET"
        val statusCode = con.responseCode
        if(statusCode != 200) {
            logger.error("Received unexpected status code when fetching UUID for player $playerName: $statusCode")
            return null
        }
        val responseJson = Gson().fromJson(con.inputStream.reader(), JsonObject::class.java)
        if(responseJson.has("errorMessage")) {
            logger.error("Received unexpected error when fetching UUID for player $playerName: ${responseJson.get("errorMessage").asString}")
            return null
        }
        val uuidStringWithoutDashes = responseJson.get("id").asString
        val mostSignificant =
            uuidStringWithoutDashes.substring(0, uuidStringWithoutDashes.length / 2).toULong(16).toLong()
        val leastSignificant =
            uuidStringWithoutDashes.substring(uuidStringWithoutDashes.length / 2).toULong(16).toLong()
        return UUID(mostSignificant, leastSignificant)
    } catch(e: Exception) {
        logger.error("Encountered unexpected error when fetching UUID for player $playerName", e)
        return null
    }
}