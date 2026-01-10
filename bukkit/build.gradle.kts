buildscript {
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
}

plugins {
    id("io.typst.spigradle.spigot")
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
}

base {
    archivesName.set("${property("archives_base_name")}-bukkit")
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle("1.21.10-R0.1-SNAPSHOT")
    implementation(project(":common"))
}

// Reproducible SNAPSHOT dependency resolution:
// 1. Lockfile uses base SNAPSHOT versions (required by Gradle's locking mechanism)
// 2. resolutionStrategy substitutes to specific timestamped builds during resolution
// 3. verification-metadata.xml checksums verify the exact artifacts downloaded
val snapshotPins = mapOf(
    "io.papermc.paper:dev-bundle" to "1.21.10-R0.1-20260104.211118-51",
    "io.papermc.paper:paper-api" to "1.21.10-R0.1-20260104.211118-51",
    "com.velocitypowered:velocity-native" to "3.4.0-20260108.171417-116",
    "me.lucko:spark-api" to "0.1-20240720.200737-2"
)

configurations.all {
    resolutionStrategy.eachDependency {
        val key = "${requested.group}:${requested.name}"
        snapshotPins[key]?.let { pinnedVersion ->
            useVersion(pinnedVersion)
        }
    }
}

tasks.jar {
    from(project(":common").sourceSets.main.get().output)
}

spigot {
    name = "ShulkerTrims"
    version = project.version.toString()
    apiVersion = "1.21"
    main = "com.wlritchi.shulkertrims.bukkit.ShulkerTrimsPlugin"
    authors = listOf("wlritchi")
    description = "Apply armor trims to shulker boxes using the smithing table"
}
