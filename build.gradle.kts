plugins {
    java
    id("fabric-loom") version "1.15.0-alpha.16" apply false
    id("io.typst.spigradle.spigot") version "4.0.0" apply false
}

subprojects {
    apply(plugin = "java")

    group = property("maven_group")!!
    version = property("mod_version")!!

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        withSourcesJar()
    }

    repositories {
        mavenCentral()
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
}
