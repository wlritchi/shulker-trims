import java.util.jar.Attributes
import java.util.jar.Manifest

/**
 * Computes project version from git tags.
 *
 * Version format:
 * - On tag `v1.0.0` or `1.0.0`: `1.0.0`
 * - 5 commits after tag: `1.0.0+5.g1234abc`
 * - Dirty working tree: appends `-dirty`
 * - No tags: `0.0.0+g1234abc-SNAPSHOT`
 * - No git: `unknown`
 */
fun computeGitVersion(): String {
    return try {
        val process = ProcessBuilder("git", "describe", "--tags", "--dirty", "--always")
            .directory(projectDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()

        if (exitCode != 0 || output.isEmpty()) {
            return "unknown"
        }

        // Parse git describe output
        // Possible formats:
        // - v1.0.0 (exact tag)
        // - 1.0.0-rc1 (exact tag without v prefix)
        // - v1.0.0-dirty (exact tag, dirty)
        // - v1.0.0-5-g1234abc (after tag)
        // - v1.0.0-5-g1234abc-dirty (after tag, dirty)
        // - 1234abc (no tags, just commit hash)
        // - 1234abc-dirty (no tags, dirty)

        val dirty = output.endsWith("-dirty")
        val cleanOutput = if (dirty) output.removeSuffix("-dirty") else output

        // Check if it's just a commit hash (no tags)
        if (cleanOutput.matches(Regex("^[0-9a-f]{7,40}$"))) {
            val version = "0.0.0+g$cleanOutput-SNAPSHOT"
            return if (dirty) "$version-dirty" else version
        }

        // Pattern for tag with commits after: v1.0.0-5-g1234abc or 1.0.0-5-g1234abc
        val afterTagPattern = Regex("^v?(.+)-([0-9]+)-g([0-9a-f]+)$")
        val afterTagMatch = afterTagPattern.matchEntire(cleanOutput)
        if (afterTagMatch != null) {
            val (tag, commits, hash) = afterTagMatch.destructured
            val version = "$tag+$commits.g$hash"
            return if (dirty) "$version-dirty" else version
        }

        // Exact tag match: v1.0.0 or 1.0.0-rc1
        val version = cleanOutput.removePrefix("v")
        return if (dirty) "$version-dirty" else version
    } catch (e: Exception) {
        "unknown"
    }
}

val gitVersion = computeGitVersion()

buildscript {
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
}

plugins {
    java
    id("fabric-loom") version "1.15.0-alpha.16" apply false
    id("io.typst.spigradle.spigot") version "4.0.0" apply false
    id("io.github.pacifistmc.forgix") version "2.0.0-SNAPSHOT.5.1"
}

group = property("maven_group")!!
version = gitVersion

base {
    archivesName.set(property("archives_base_name").toString())
}

forgix {
    archiveClassifier = "mc${property("minecraft_version")}"
    merge("fabric") {
        inputJar.set(layout.projectDirectory.file("fabric/build/libs/${property("archives_base_name")}-fabric-${gitVersion}.jar"))
    }
    merge("bukkit") {
        inputJar.set(layout.projectDirectory.file("bukkit/build/libs/${property("archives_base_name")}-bukkit-${gitVersion}.jar"))
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

dependencyLocking {
    lockAllConfigurations()
}

subprojects {
    apply(plugin = "java")

    group = property("maven_group")!!
    version = rootProject.version

    dependencyLocking {
        lockAllConfigurations()
    }

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
