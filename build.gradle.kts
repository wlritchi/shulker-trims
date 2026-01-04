import java.util.jar.Attributes
import java.util.jar.Manifest

plugins {
    java
    id("fabric-loom") version "1.15.0-alpha.16" apply false
    id("io.typst.spigradle.spigot") version "4.0.0" apply false
    id("io.github.pacifistmc.forgix") version "2.0.0-SNAPSHOT.5.1"
}

group = property("maven_group")!!
version = property("mod_version")!!

base {
    archivesName.set(property("archives_base_name").toString())
}

forgix {
    archiveClassifier = "mc${property("minecraft_version")}"
    merge("fabric") {
        inputJar.set(layout.projectDirectory.file("fabric/build/libs/${property("archives_base_name")}-fabric-${property("mod_version")}.jar"))
    }
    merge("bukkit") {
        inputJar.set(layout.projectDirectory.file("bukkit/build/libs/${property("archives_base_name")}-bukkit-${property("mod_version")}.jar"))
    }
}

// Fix manifest line length issue caused by Forgix not wrapping long lines
tasks.register("fixMergedJarManifest") {
    dependsOn("mergeJars")
    mustRunAfter(subprojects.map { it.tasks.named("build") })
    doLast {
        val forgixDir = layout.buildDirectory.dir("forgix").get().asFile
        forgixDir.listFiles()?.filter { it.extension == "jar" }?.forEach { jarFile ->
            val tempDir = layout.buildDirectory.dir("forgix-temp").get().asFile
            tempDir.deleteRecursively()
            tempDir.mkdirs()

            // Extract JAR
            copy {
                from(zipTree(jarFile))
                into(tempDir)
            }

            // Parse and fix manifest manually, then write with proper line wrapping
            val manifestFile = tempDir.resolve("META-INF/MANIFEST.MF")
            if (manifestFile.exists()) {
                val manifest = Manifest()
                val attrs = manifest.mainAttributes
                attrs[Attributes.Name.MANIFEST_VERSION] = "1.0"

                // Parse the malformed manifest line by line
                manifestFile.readText().lines().forEach { line ->
                    if (line.contains(": ")) {
                        val idx = line.indexOf(": ")
                        val key = line.substring(0, idx)
                        val value = line.substring(idx + 2)
                        if (key != "Manifest-Version") {
                            attrs[Attributes.Name(key)] = value
                        }
                    }
                }

                manifestFile.outputStream().use { manifest.write(it) }
            }

            // Repackage JAR
            ant.withGroovyBuilder {
                "jar"("destfile" to jarFile, "basedir" to tempDir, "manifest" to manifestFile)
            }

            tempDir.deleteRecursively()
        }
    }
}

tasks.named("mergeJars") {
    dependsOn(":fabric:build", ":bukkit:build")
}

tasks.named("build") {
    dependsOn("fixMergedJarManifest")
}

// Aggregates slow tests that shouldn't run on every build.
// CI should run: ./gradlew build slowTest
tasks.register("slowTest") {
    group = "verification"
    description = "Runs slow tests (game tests, integration tests, etc.)"
    dependsOn(":fabric:runGameTest")
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
