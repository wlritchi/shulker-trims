plugins {
    id("io.typst.spigradle.spigot")
}

base {
    archivesName.set("${property("archives_base_name")}-bukkit")
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
    implementation(project(":common"))
}

spigot {
    name = "ShulkerTrims"
    version = project.version.toString()
    apiVersion = "1.21"
    main = "com.wlritchi.shulkertrims.bukkit.ShulkerTrimsPlugin"
    authors = listOf("wlritchi")
    description = "Apply armor trims to shulker boxes using the smithing table"
}
