pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.pacifistmc.net/") { name = "Forgix" }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
    versionCatalogs {
        create("spigots") {
            from("io.typst:spigot-catalog:1.0.0")
        }
    }
}

rootProject.name = "shulker-trims"

include("common")
include("fabric")
include("bukkit")
