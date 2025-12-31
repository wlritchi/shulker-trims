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
