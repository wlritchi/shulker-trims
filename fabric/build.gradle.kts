import groovy.json.JsonSlurper
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.util.Base64
import javax.imageio.ImageIO

buildscript {
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
}

plugins {
    id("fabric-loom")
}

// Task to extract shulker trim PNGs from BlockBench model file
abstract class ExtractShulkerTrims : DefaultTask() {
    @get:InputFile
    abstract val bbmodelFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private val trimPatterns = listOf(
        "sentry", "vex", "wild", "coast", "dune", "wayfinder",
        "raiser", "shaper", "host", "ward", "silence", "tide",
        "snout", "rib", "eye", "spire", "flow", "bolt"
    )

    @TaskAction
    fun extract() {
        val bbmodel = bbmodelFile.get().asFile
        if (!bbmodel.exists()) {
            throw GradleException("BBModel file not found: ${bbmodel.absolutePath}")
        }

        val json = try {
            @Suppress("UNCHECKED_CAST")
            JsonSlurper().parse(bbmodel) as Map<String, Any>
        } catch (e: Exception) {
            throw GradleException("Failed to parse bbmodel: ${e.message}")
        }

        @Suppress("UNCHECKED_CAST")
        val textures = json["textures"] as? List<Map<String, Any>>
            ?: throw GradleException("No textures found in bbmodel")

        if (textures.isEmpty()) {
            throw GradleException("Textures array is empty in bbmodel")
        }

        @Suppress("UNCHECKED_CAST")
        val layers = textures[0]["layers"] as? List<Map<String, Any>>
            ?: throw GradleException("No layers found in bbmodel texture")

        val layersByName = layers.associateBy { it["name"] as String }
        val outDir = outputDir.get().asFile
        outDir.mkdirs()

        for (pattern in trimPatterns) {
            val layer = layersByName[pattern]
                ?: throw GradleException("Layer '$pattern' not found in bbmodel")

            val dataUrl = layer["data_url"] as? String
                ?: throw GradleException("No data_url for layer '$pattern'")

            val base64Data = dataUrl.substringAfter("base64,")
            val pngBytes = try {
                Base64.getDecoder().decode(base64Data)
            } catch (e: Exception) {
                throw GradleException("Failed to decode PNG data for layer '$pattern': ${e.message}")
            }

            // Read the image and pad to 64x64 if needed
            val sourceImage = try {
                ImageIO.read(ByteArrayInputStream(pngBytes))
            } catch (e: Exception) {
                throw GradleException("Failed to read PNG for layer '$pattern': ${e.message}")
            }

            val outputImage = if (sourceImage.width == 64 && sourceImage.height == 64) {
                sourceImage
            } else {
                // Create 64x64 canvas with transparency and draw source at origin
                val padded = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
                padded.createGraphics().apply {
                    drawImage(sourceImage, 0, 0, null)
                    dispose()
                }
                padded
            }

            val outFile = File(outDir, "$pattern.png")
            ImageIO.write(outputImage, "PNG", outFile)
            logger.info("Extracted $pattern.png (${sourceImage.width}x${sourceImage.height} -> 64x64)")
        }

        logger.lifecycle("Extracted ${trimPatterns.size} shulker trim textures")
    }
}

// Register and configure the extraction task
val generatedResources = layout.buildDirectory.dir("generated/resources/shulker-trims")

val extractShulkerTrims by tasks.registering(ExtractShulkerTrims::class) {
    bbmodelFile.set(rootProject.file("art/shulker-trims.bbmodel"))
    outputDir.set(generatedResources.map { it.dir("assets/shulker_trims/textures/trims/entity/shulker") })
}

// Add generated resources to source sets
sourceSets.main {
    resources.srcDir(generatedResources)
}

// Ensure extraction runs before resource processing
tasks.processResources {
    dependsOn(extractShulkerTrims)
    // Exclude reference textures (design aids, not for distribution)
    exclude("**/reference_*")
}

// Also ensure sources jar depends on extraction
tasks.named("sourcesJar") {
    dependsOn(extractShulkerTrims)
}

base {
    archivesName.set("${property("archives_base_name")}-fabric")
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register("shulker_trims") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets.getByName("client"))
        }
    }
}

fabricApi {
    configureTests {
        createSourceSet = true
        modId = "shulker_trims_test"
        enableGameTests = true
        enableClientGameTests = true
        eula = true
    }
}

// Icon generation source set - separate from tests to isolate OrthoCamera dependency
sourceSets {
    create("icongen") {
        compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
        // Also need client classes for rendering
        compileClasspath += sourceSets.getByName("client").compileClasspath + sourceSets.getByName("client").output
        runtimeClasspath += sourceSets.getByName("client").runtimeClasspath + sourceSets.getByName("client").output
    }
}

// Create modIcongenImplementation and related configurations that remap mods
loom.createRemapConfigurations(sourceSets.getByName("icongen"))

// OrthoCamera for isometric icon generation (isolated to icongen source set)
dependencies {
    "modIcongenImplementation"("maven.modrinth:orthocamera:0.1.10+1.21.9")
}

// Note: For headless client game tests on Linux, use:
//   xvfb-run -a ./gradlew :fabric:runClientGameTest
// This ensures consistent rendering between local and CI environments.

repositories {
    maven("https://maven.fabricmc.net/")
    maven("https://repo.opencollab.dev/main/") {
        name = "opencollab"
    }
    maven("https://repo.opencollab.dev/maven-snapshots/") {
        name = "opencollab-snapshots"
    }
    maven("https://api.modrinth.com/maven") {
        name = "Modrinth"
        content {
            includeGroup("maven.modrinth")
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")

    implementation(project(":common"))

    // MCProtocolLib for bot client in two-player tests
    // Using 1.21.9-SNAPSHOT (protocol-compatible with MC 1.21.10)
    // Exclude netty-all to avoid version conflict with Minecraft's bundled netty 4.1.x
    // MCProtocolLib 1.21.9 pulls netty 4.2.1 which has incompatible IoHandler changes
    // that break LocalServerChannel used by the integrated server
    "gametestImplementation"("org.geysermc.mcprotocollib:protocol:1.21.9-SNAPSHOT") {
        exclude(group = "io.netty")
    }

}

// Reproducible SNAPSHOT dependency resolution (same pattern as bukkit module):
// 1. Dependencies use base SNAPSHOT versions
// 2. resolutionStrategy substitutes to specific timestamped builds during resolution
// 3. verification-metadata.xml checksums verify the exact artifacts downloaded
val snapshotPins = mapOf(
    "org.geysermc.mcprotocollib:protocol" to "1.21.9-20251210.010914-21"
)

configurations.all {
    resolutionStrategy.eachDependency {
        val key = "${requested.group}:${requested.name}"
        snapshotPins[key]?.let { pinnedVersion ->
            useVersion(pinnedVersion)
        }
    }
}

// Include common module classes in the fabric jar
tasks.jar {
    from(project(":common").sourceSets.main.get().output)
}

tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
    // Ensure common classes are included before remapping
    dependsOn(project(":common").tasks.named("classes"))
}

tasks.processResources {
    inputs.property("version", project.version)

    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

// Don't run game tests as part of the standard build - they're slow.
// Use ./gradlew slowTest to run them explicitly.
afterEvaluate {
    tasks.named("test") {
        setDependsOn(dependsOn.filterNot { dep ->
            dep.toString().contains("runGameTest")
        })
    }

    // Configure client game tests
    tasks.named<JavaExec>("runClientGameTest") {
        // Ensure Bukkit plugin is built for Paper server tests
        dependsOn(":bukkit:build")

        // Disable network synchronization to allow external server connections
        jvmArgs("-Dfabric.client.gametest.disableNetworkSynchronizer=true")

        // Pass through shulker_trims.* system properties to the Minecraft JVM
        // This allows filtering which Paper tests to run, e.g.:
        //   ./gradlew :fabric:runClientGameTest -Dshulker_trims.paper_tests=world,chest_gui
        //   ./gradlew :fabric:runClientGameTest -Dshulker_trims.test_mode=fabric
        System.getProperties().forEach { key, value ->
            if (key.toString().startsWith("shulker_trims.")) {
                jvmArgs("-D$key=$value")
            }
        }
    }
}

// Icon generation task - runs icongen source set as a client game test
loom {
    runs {
        register("icongen") {
            client()
            name = "Generate Icons"
            source(sourceSets.getByName("icongen"))
            // Use the same configuration as client game tests
            property("fabric-api.gametest")
            property("fabric-api.gametest.client")
        }
    }
}

// Configure the icongen run task to pass through CLI arguments
afterEvaluate {
    tasks.named<JavaExec>("runIcongen") {
        // Pass through shulker_trims.icongen.* system properties
        System.getProperties().forEach { key, value ->
            if (key.toString().startsWith("shulker_trims.icongen.")) {
                jvmArgs("-D$key=$value")
            }
        }

        // Set default output directory
        val outputDir = project.layout.buildDirectory.dir("icons").get().asFile
        jvmArgs("-Dshulker_trims.icongen.output=${outputDir.absolutePath}")
    }
}

// Convenience task with clearer name
tasks.register("generateIcon") {
    group = "shulker trims"
    description = "Generate isometric icon renders of trimmed shulker boxes"
    dependsOn("runIcongen")
}

