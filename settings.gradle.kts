pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net") { name = "Fabric" }
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("fabric-loom").version(System.getProperty("loom_version"))
        kotlin("jvm").version(System.getProperty("kotlin_version"))
    }
}